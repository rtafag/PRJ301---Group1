import { createHash, createHmac, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';

const JWT_SECRET = process.env.JWT_SECRET || 'aita-secret-key-prj301-group1-2026';

function base64UrlEncode(strOrBuffer) {
  const buf = Buffer.isBuffer(strOrBuffer) ? strOrBuffer : Buffer.from(strOrBuffer);
  return buf.toString('base64url');
}

function base64UrlDecode(str) {
  return Buffer.from(str, 'base64url').toString('utf8');
}

export function signJwt(payload, secret = JWT_SECRET) {
  const header = { alg: 'HS256', typ: 'JWT' };
  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedPayload = base64UrlEncode(JSON.stringify(payload));
  const data = `${encodedHeader}.${encodedPayload}`;
  const signature = createHmac('sha256', secret).update(data).digest();
  const encodedSignature = base64UrlEncode(signature);
  return `${data}.${encodedSignature}`;
}

export function verifyJwt(token, secret = JWT_SECRET) {
  if (typeof token !== 'string') return null;
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const [encodedHeader, encodedPayload, encodedSignature] = parts;

  const data = `${encodedHeader}.${encodedPayload}`;
  const expectedSig = createHmac('sha256', secret).update(data).digest();
  const providedSig = Buffer.from(encodedSignature, 'base64url');

  if (expectedSig.length !== providedSig.length || !timingSafeEqual(expectedSig, providedSig)) {
    return null;
  }

  try {
    const payload = JSON.parse(base64UrlDecode(encodedPayload));
    if (payload.exp && Date.now() >= payload.exp * 1000) {
      return null;
    }
    return payload;
  } catch {
    return null;
  }
}

function hashPassword(password) {
  const salt = randomBytes(16).toString('hex');
  const digest = scryptSync(String(password), salt, 64).toString('hex');
  return `$scrypt$${salt}$${digest}`;
}

function matchesPasswordHash(candidate, expected) {
  if (String(expected).startsWith('$scrypt$')) {
    const [, , salt, digest] = String(expected).split('$');
    if (!salt || !digest) return false;
    const actual = scryptSync(String(candidate), salt, Buffer.from(digest, 'hex').length);
    return timingSafeEqual(actual, Buffer.from(digest, 'hex'));
  }
  const candidateDigest = createHash('sha256').update(String(candidate)).digest();
  const expectedDigest = createHash('sha256').update(String(expected)).digest();
  return timingSafeEqual(candidateDigest, expectedDigest);
}

export function createAuth(store) {
  const revokedTokens = new Set();
  const lifetimeMs = 8 * 60 * 60 * 1000;

  return {
    login({ username, passwordHash }) {
      const user = store.findUser(String(username ?? '').trim());
      if (!user || !matchesPasswordHash(passwordHash ?? '', user.passwordHash)) return null;

      const nowSec = Math.floor(Date.now() / 1000);
      const expSec = nowSec + Math.floor(lifetimeMs / 1000);
      const payload = {
        sub: user.userId,
        userId: user.userId,
        username: user.username,
        fullName: user.fullName,
        role: user.role,
        iat: nowSec,
        exp: expSec
      };

      const token = signJwt(payload);
      const userSession = {
        userId: user.userId,
        username: user.username,
        fullName: user.fullName,
        role: user.role,
        expiresAt: expSec * 1000
      };
      return { token, user: userSession };
    },
    resetPassword({ email, confirmationKey, newPassword }) {
      const password = String(newPassword ?? '');
      if (password.length < 8) return false;
      return store.resetPassword(
        String(email ?? '').trim().toLowerCase(),
        String(confirmationKey ?? '').trim(),
        hashPassword(password)
      );
    },
    changePassword({ userId, currentPassword, newPassword }) {
      const user = store.findUserById(userId);
      const password = String(newPassword ?? '');
      if (!user || password.length < 8 || !matchesPasswordHash(currentPassword ?? '', user.passwordHash)) return false;
      return store.updateUserPassword(userId, hashPassword(password));
    },
    getUser(token) {
      if (!token || revokedTokens.has(token)) return null;
      const claims = verifyJwt(token);
      if (!claims) return null;
      return {
        userId: claims.userId || claims.sub,
        username: claims.username,
        fullName: claims.fullName,
        role: claims.role,
        expiresAt: claims.exp ? claims.exp * 1000 : Date.now() + lifetimeMs
      };
    },
    logout(token) {
      if (token) {
        revokedTokens.add(token);
      }
    }
  };
}

export function requireAuth(auth, role) {
  return (request, response, next) => {
    const authorization = request.get('authorization') ?? '';
    const token = authorization.startsWith('Bearer ') ? authorization.slice(7) : '';
    const user = auth.getUser(token);
    if (!user) return response.status(401).json({ error: 'Authentication required.' });
    if (role && user.role.toLowerCase() !== role.toLowerCase()) {
      return response.status(403).json({ error: 'This action is not allowed for your role.' });
    }
    request.user = user;
    request.sessionToken = token;
    return next();
  };
}
