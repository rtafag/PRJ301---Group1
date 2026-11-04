import net from 'node:net';
import tls from 'node:tls';

const PRIORITY_SCORE_FACTOR = 100_000_000_000_000;
const DEFAULT_REDIS_URL = 'redis://127.0.0.1:6379';
const ENQUEUE_SCRIPT = `
  if redis.call('HEXISTS', KEYS[3], ARGV[1]) == 1 then
    return 0
  end
  redis.call('HSET', KEYS[3], ARGV[1], ARGV[2])
  redis.call('ZADD', KEYS[1], ARGV[3], ARGV[1])
  return 1
`;
const RESERVE_SCRIPT = `
  local now = tonumber(ARGV[1])
  local leaseUntil = now + tonumber(ARGV[2])
  local expired = redis.call('ZRANGEBYSCORE', KEYS[2], '-inf', now, 'LIMIT', 0, 1)
  if #expired > 0 then
    local id = expired[1]
    local raw = redis.call('HGET', KEYS[3], id)
    redis.call('ZREM', KEYS[2], id)
    if raw then
      local job = cjson.decode(raw)
      if job.attemptsMade >= job.maxAttempts then
        redis.call('HDEL', KEYS[3], id)
        redis.call('LPUSH', KEYS[4], raw)
      else
        job.dueAt = now + math.min(1000 * (2 ^ (job.attemptsMade - 1)), 30000)
        raw = cjson.encode(job)
        redis.call('HSET', KEYS[3], id, raw)
        redis.call('ZADD', KEYS[1], job.priority * 100000000000000 + job.dueAt, id)
      end
    end
  end
  local failed = redis.call('LPOP', KEYS[4])
  if failed then
    return { 'failed', failed }
  end
  local ids = redis.call('ZRANGE', KEYS[1], 0, 99)
  for _, id in ipairs(ids) do
    local raw = redis.call('HGET', KEYS[3], id)
    if not raw then
      redis.call('ZREM', KEYS[1], id)
    else
      local job = cjson.decode(raw)
      if tonumber(job.dueAt) <= now then
        job.attemptsMade = job.attemptsMade + 1
        raw = cjson.encode(job)
        redis.call('ZREM', KEYS[1], id)
        redis.call('HSET', KEYS[3], id, raw)
        redis.call('ZADD', KEYS[2], leaseUntil, id)
        return { 'job', raw }
      end
    end
  end
  return {}
`;
const RETRY_SCRIPT = `
  local raw = redis.call('HGET', KEYS[2], ARGV[1])
  if not raw then
    return 0
  end
  redis.call('ZREM', KEYS[1], ARGV[1])
  local job = cjson.decode(raw)
  job.dueAt = tonumber(ARGV[2]) + tonumber(ARGV[3])
  raw = cjson.encode(job)
  redis.call('HSET', KEYS[2], ARGV[1], raw)
  redis.call('ZADD', KEYS[3], job.priority * 100000000000000 + job.dueAt, ARGV[1])
  return 1
`;
const ACK_SCRIPT = `
  redis.call('ZREM', KEYS[1], ARGV[1])
  redis.call('ZREM', KEYS[2], ARGV[1])
  redis.call('HDEL', KEYS[3], ARGV[1])
  return 1
`;
const HEARTBEAT_SCRIPT = `
  if redis.call('ZSCORE', KEYS[1], ARGV[1]) then
    redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1])
    return 1
  end
  return 0
`;

function parseResp(buffer, offset = 0) {
  if (offset >= buffer.length) return null;
  const prefix = String.fromCharCode(buffer[offset]);
  const lineEnd = buffer.indexOf('\r\n', offset);
  if (lineEnd < 0) return null;
  const header = buffer.toString('utf8', offset + 1, lineEnd);
  const nextOffset = lineEnd + 2;

  if (prefix === '+' || prefix === '-') {
    return {
      value: prefix === '-' ? new Error(header) : header,
      offset: nextOffset
    };
  }
  if (prefix === ':') {
    return { value: Number(header), offset: nextOffset };
  }
  if (prefix === '$') {
    const length = Number(header);
    if (length === -1) return { value: null, offset: nextOffset };
    if (!Number.isSafeInteger(length) || length < 0) throw new Error('Invalid Redis bulk string length.');
    const end = nextOffset + length;
    if (buffer.length < end + 2) return null;
    return { value: buffer.toString('utf8', nextOffset, end), offset: end + 2 };
  }
  if (prefix === '*') {
    const count = Number(header);
    if (count === -1) return { value: null, offset: nextOffset };
    if (!Number.isSafeInteger(count) || count < 0) throw new Error('Invalid Redis array length.');
    const values = [];
    let cursor = nextOffset;
    for (let index = 0; index < count; index += 1) {
      const item = parseResp(buffer, cursor);
      if (!item) return null;
      values.push(item.value);
      cursor = item.offset;
    }
    return { value: values, offset: cursor };
  }
  throw new Error(`Unsupported Redis response type: ${prefix}`);
}

function encodeCommand(args) {
  const buffers = [Buffer.from(`*${args.length}\r\n`)];
  for (const argument of args) {
    const value = Buffer.from(String(argument));
    buffers.push(Buffer.from(`$${value.length}\r\n`), value, Buffer.from('\r\n'));
  }
  return Buffer.concat(buffers);
}

function redisError(error) {
  return error instanceof Error ? error : null;
}

function getRedisCommands(redisUrl, command) {
  const commands = [];
  if (redisUrl.password) {
    commands.push(redisUrl.username
      ? ['AUTH', decodeURIComponent(redisUrl.username), decodeURIComponent(redisUrl.password)]
      : ['AUTH', decodeURIComponent(redisUrl.password)]);
  }
  const database = redisUrl.pathname.slice(1);
  if (database && database !== '0') {
    if (!/^\d+$/.test(database)) throw new Error('REDIS_URL must use a numeric Redis database path.');
    commands.push(['SELECT', database]);
  }
  commands.push(command);
  return commands;
}

async function sendRedisCommand(redisUrl, command) {
  if (redisUrl.protocol !== 'redis:' && redisUrl.protocol !== 'rediss:') {
    throw new Error('REDIS_URL must use redis:// or rediss://.');
  }
  const port = Number(redisUrl.port) || 6379;
  const commands = getRedisCommands(redisUrl, command);

  return new Promise((resolve, reject) => {
    const secure = redisUrl.protocol === 'rediss:';
    const socket = secure
      ? tls.connect({ host: redisUrl.hostname, port, servername: redisUrl.hostname })
      : net.createConnection({ host: redisUrl.hostname, port });
    let buffer = Buffer.alloc(0);
    let responseCount = 0;
    let settled = false;

    const finish = (error, value) => {
      if (settled) return;
      settled = true;
      socket.destroy();
      if (error) reject(error);
      else resolve(value);
    };

    socket.setTimeout(5000, () => finish(new Error('Redis connection timed out.')));
    socket.once(secure ? 'secureConnect' : 'connect', () => {
      socket.write(Buffer.concat(commands.map(encodeCommand)));
    });
    socket.on('data', (chunk) => {
      buffer = Buffer.concat([buffer, chunk]);
      if (buffer.length > 4 * 1024 * 1024) {
        return finish(new Error('Redis response exceeded the 4 MB limit.'));
      }
      try {
        while (responseCount < commands.length) {
          const parsed = parseResp(buffer);
          if (!parsed) return;
          buffer = buffer.subarray(parsed.offset);
          responseCount += 1;
          const error = redisError(parsed.value);
          if (error) return finish(error);
          if (responseCount === commands.length) finish(null, parsed.value);
        }
      } catch (error) {
        finish(error);
      }
    });
    socket.once('error', (error) => finish(error));
    socket.once('close', () => {
      if (!settled) finish(new Error('Redis closed the connection before replying.'));
    });
  });
}

function getQueueScore(priority, dueAt) {
  return priority * PRIORITY_SCORE_FACTOR + dueAt;
}

export function getSubmissionPriority(assignment, now = Date.now()) {
  if (!assignment) return 10;
  const dueAt = new Date(assignment.dueAt).getTime();
  if (!Number.isFinite(dueAt) || dueAt - now <= 24 * 60 * 60 * 1000) return 1;
  if (dueAt - now <= 7 * 24 * 60 * 60 * 1000) return 5;
  return 10;
}

export function getRetryDelay(attemptsMade) {
  if (!Number.isInteger(attemptsMade) || attemptsMade < 1) {
    throw new RangeError('attemptsMade must be a positive integer.');
  }
  return Math.min(1000 * (2 ** (attemptsMade - 1)), 30000);
}

export class AnalysisQueue {
  constructor({
    url = process.env.REDIS_URL || DEFAULT_REDIS_URL,
    prefix = process.env.REDIS_QUEUE_PREFIX || 'aita:analysis'
  } = {}) {
    this.redisUrl = new URL(url);
    this.keys = {
      waiting: `${prefix}:waiting`,
      processing: `${prefix}:processing`,
      jobs: `${prefix}:jobs`,
      failed: `${prefix}:failed`
    };
  }

  async ping() {
    return sendRedisCommand(this.redisUrl, ['PING']);
  }

  async enqueue({ submissionId, priority = 10, maxAttempts = 3 }) {
    if (typeof submissionId !== 'string' || !submissionId) {
      throw new TypeError('submissionId is required to enqueue analysis.');
    }
    if (!Number.isInteger(priority) || priority < 1 || priority > 10) {
      throw new RangeError('priority must be an integer from 1 to 10.');
    }
    if (!Number.isInteger(maxAttempts) || maxAttempts < 1) {
      throw new RangeError('maxAttempts must be a positive integer.');
    }
    const now = Date.now();
    const job = {
      submissionId,
      priority,
      maxAttempts,
      attemptsMade: 0,
      dueAt: now
    };
    const result = await sendRedisCommand(this.redisUrl, [
      'EVAL', ENQUEUE_SCRIPT, 3,
      this.keys.waiting, this.keys.processing, this.keys.jobs,
      submissionId, JSON.stringify(job), getQueueScore(priority, now)
    ]);
    return result === 1;
  }

  async reserve({ leaseMs = 120000 } = {}) {
    const result = await sendRedisCommand(this.redisUrl, [
      'EVAL', RESERVE_SCRIPT, 4,
      this.keys.waiting, this.keys.processing, this.keys.jobs, this.keys.failed,
      Date.now(), leaseMs
    ]);
    if (!Array.isArray(result) || result.length === 0) return null;
    return { type: result[0], job: JSON.parse(result[1]) };
  }

  async retry(job, delayMs) {
    const result = await sendRedisCommand(this.redisUrl, [
      'EVAL', RETRY_SCRIPT, 3,
      this.keys.processing, this.keys.jobs, this.keys.waiting,
      job.submissionId, Date.now(), delayMs
    ]);
    return result === 1;
  }

  async acknowledge(submissionId) {
    await sendRedisCommand(this.redisUrl, [
      'EVAL', ACK_SCRIPT, 3,
      this.keys.waiting, this.keys.processing, this.keys.jobs, submissionId
    ]);
  }

  async heartbeat(submissionId, leaseMs = 120000) {
    const result = await sendRedisCommand(this.redisUrl, [
      'EVAL', HEARTBEAT_SCRIPT, 1,
      this.keys.processing, submissionId, Date.now() + leaseMs
    ]);
    return result === 1;
  }

  async clear() {
    await sendRedisCommand(this.redisUrl, [
      'DEL', this.keys.waiting, this.keys.processing, this.keys.jobs, this.keys.failed
    ]);
  }
}
