import { createHash, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';

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
  const sessions = new Map();
  const lifetimeMs = 8 * 60 * 60 * 1000;

  return {
    login({ username, passwordHash }) {
      const user = store.findUser(String(username ?? '').trim());
      if (!user || !matchesPasswordHash(passwordHash ?? '', user.passwordHash)) return null;

      const token = randomBytes(32).toString('base64url');
      sessions.set(token, {
        userId: user.userId,
        username: user.username,
        fullName: user.fullName,
        role: user.role,
        expiresAt: Date.now() + lifetimeMs
      });
      return { token, user: sessions.get(token) };
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
    getUser(token) {
      const session = sessions.get(token);
      if (!session) return null;
      if (session.expiresAt <= Date.now()) {
        sessions.delete(token);
        return null;
      }
      return session;
    },
    logout(token) {
      sessions.delete(token);
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