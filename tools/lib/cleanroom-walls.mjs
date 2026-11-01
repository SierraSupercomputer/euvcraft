import { deflateSync, crc32 } from 'node:zlib';
import { FACE_EDGES } from './cleanroom-windows.mjs';
const A = 'src/main/resources/assets/lithmod/';

function chunk(type, data) {
    const t = Buffer.from(type);
    const n = Buffer.alloc(4);
    const c = Buffer.alloc(4);
    n.writeUInt32BE(data.length);
    c.writeUInt32BE(crc32(Buffer.concat([t, data])) >>> 0);
    return Buffer.concat([n, t, data, c]);
}

function tile(mask) {
    const h = Buffer.alloc(13);
    h.writeUInt32BE(16);
    h.writeUInt32BE(16, 4);
    h[8] = 8;
    h[9] = 6;
    const rows = Buffer.alloc(16 * 65);
    for (let y = 0; y < 16; y++) {
        for (let x = 0; x < 16; x++) {
            const edges = [x < 2, x >= 14, y < 2, y >= 14];
            const leftEdge = edges[0];
            const rightEdge = edges[1];
            const topEdge = edges[2];
            const bottomEdge = edges[3];

            const leftJoined = Boolean(mask & 1);
            const rightJoined = Boolean(mask & 2);
            const topJoined = Boolean(mask & 4);
            const bottomJoined = Boolean(mask & 8);

            const isBorder = (leftEdge && !leftJoined) ||
                             (rightEdge && !rightJoined) ||
                             (topEdge && !topJoined) ||
                             (bottomEdge && !bottomJoined);

            const isOuter = (x === 0 && !leftJoined) ||
                            (x === 15 && !rightJoined) ||
                            (y === 0 && !topJoined) ||
                            (y === 15 && !bottomJoined);

            let rgba;
            if (isBorder) {
                rgba = isOuter ? [80, 100, 107, 255] : [217, 228, 231, 255];
            } else {
                rgba = [237, 243, 244, 255];
            }
            for (let c = 0; c < 4; c++) rows[y * 65 + 1 + x * 4 + c] = rgba[c];
        }
    }
    return Buffer.concat([
        Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
        chunk('IHDR', h),
        chunk('IDAT', deflateSync(rows, { level: 9 })),
        chunk('IEND', Buffer.alloc(0))
    ]);
}

/** Four-edge joins work on all six faces, opaque wall panels with no CTM dependency. */
export function connectedWallAssets() {
    const out = new Map();
    const put = (path, value) => out.set(A + path, Buffer.isBuffer(value) ? value : Buffer.from(JSON.stringify(value, null, 2) + '\n'));
    const multipart = [];

    for (let mask = 0; mask < 16; mask++) {
        put(`textures/block/cleanroom_wall_join_${mask}.png`, tile(mask));
    }

    for (const [face, edges] of Object.entries(FACE_EDGES)) {
        for (let mask = 0; mask < 16; mask++) {
            const id = `cleanroom_wall_${face}_${mask}`;
            const when = Object.fromEntries(edges.map((edge, i) => [edge, String(Boolean(mask & (1 << i)))]));
            multipart.push({ when, apply: { model: 'lithmod:block/' + id } });
            put('models/block/' + id + '.json', {
                textures: {
                    particle: 'lithmod:block/cleanroom_wall',
                    panel: `lithmod:block/cleanroom_wall_join_${mask}`
                },
                elements: [{
                    from: [0, 0, 0],
                    to: [16, 16, 16],
                    faces: {
                        [face]: {
                            texture: '#panel',
                            uv: [0, 0, 16, 16],
                            cullface: face
                        }
                    }
                }]
            });
        }
    }

    put('blockstates/cleanroom_wall.json', { multipart });
    return out;
}