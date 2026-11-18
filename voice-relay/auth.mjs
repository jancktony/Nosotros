const text = new TextEncoder();
const base64url = value => {
  const padded = value.replace(/-/g, '+').replace(/_/g, '/');
  return Uint8Array.from(atob(padded), c => c.charCodeAt(0));
};
let keyPromise, previousJwk;
export async function verifyTicket(token, jwkJson, now = Math.floor(Date.now() / 1000)) {
  if (typeof token !== 'string' || token.length > 5000) throw Error('auth');
  const parts = token.split('.');
  if (parts.length !== 3) throw Error('auth');
  const header = JSON.parse(new TextDecoder().decode(base64url(parts[0])));
  const claims = JSON.parse(new TextDecoder().decode(base64url(parts[1])));
  if (header.alg !== 'RS256' || header.typ !== 'JWT' || claims.iss !== 'nosotros-voice' ||
      claims.aud !== 'nosotros-voice-v1' || !['owner', 'partner'].includes(claims.role) ||
      !/^[a-f0-9]{64}$/.test(claims.sub) || !/^[a-f0-9]{64}$/.test(claims.room) ||
      !Number.isInteger(claims.exp) || !Number.isInteger(claims.iat) ||
      claims.iat > now + 30 || claims.exp <= now || claims.exp - claims.iat > 86400)
    throw Error('auth');
  if (previousJwk !== jwkJson) {
    previousJwk = jwkJson;
    keyPromise = crypto.subtle.importKey('jwk', JSON.parse(jwkJson),
      {name:'RSASSA-PKCS1-v1_5', hash:'SHA-256'}, false, ['verify']);
  }
  const valid = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', await keyPromise,
    base64url(parts[2]), text.encode(parts[0] + '.' + parts[1]));
  if (!valid) throw Error('auth');
  return claims;
}
export function usableAttachment(value, now = Date.now()) {
  return value && ['owner','partner'].includes(value.role) &&
    typeof value.sub === 'string' && value.exp * 1000 > now;
}
