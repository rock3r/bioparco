// Ported from Anatomy by Ryan (https://github.com/wheresryan22/anatomy), MIT licensed.
// See anatomy/LICENSE.
package dev.sebastiano.anatomy.kit

/**
 * Line and fill tones. Solids use the first four; lines may also be `Lit` or a colour channel. It
 * lives with the geometry because a tube's rings carry theirs (`Rings.tone`), as the original's
 * pieces carry `ringTone`.
 */
enum class Tone {
    Hi,
    Mid,
    Lo,
    Faint,
    Lit,
    Red,
    Green,
    Blue,
}
