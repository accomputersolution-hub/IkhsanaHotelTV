/**
 * Unit tests for next-IP allocation (no root / live wg required).
 */
const assert = require("assert");
const { nextClientIp, extractHostOctets, findPeerAllowedIp } = require("../server");

const sampleConf = `
[Interface]
Address = 10.0.0.1/24
PrivateKey = AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEE=
ListenPort = 51820

[Peer]
PublicKey = bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb=
AllowedIPs = 10.0.0.2/32

[Peer]
PublicKey = ccccccccccccccccccccccccccccccccccccccccccc=
AllowedIPs = 10.0.0.3/32
`;

assert.deepStrictEqual(
  [...extractHostOctets(sampleConf)].sort((a, b) => a - b),
  [1, 2, 3],
);
assert.strictEqual(nextClientIp(sampleConf), "10.0.0.4");

assert.strictEqual(
  findPeerAllowedIp(sampleConf, "ccccccccccccccccccccccccccccccccccccccccccc="),
  "10.0.0.3",
);
assert.strictEqual(
  findPeerAllowedIp(sampleConf, "unknown="),
  null,
);

assert.strictEqual(
  nextClientIp("[Interface]\nAddress = 10.0.0.1/24\n"),
  "10.0.0.2",
);

console.log("next-ip tests OK");
