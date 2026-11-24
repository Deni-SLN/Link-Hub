// SPDX-License-Identifier: GPL-3.0-or-later
//! The LinkHub app icon (`assets/brand/icon-512.png` / `assets/brand/icon-256.rgba`).
//! Resampled in code to any requested pixel size for the tray, window, toast,
//! and `.exe` / desktop icons so every surface uses the exact same brand mark.

const SRC_SIZE: usize = 256;
const ICON_256: &[u8; SRC_SIZE * SRC_SIZE * 4] = include_bytes!("../../../assets/brand/icon-256.rgba");

/// Renders the LinkHub icon as straight-alpha RGBA, row by row, `size`×`size`.
pub fn render(size: usize) -> Vec<u8> {
    if size == 0 {
        return Vec::new();
    }
    if size == SRC_SIZE {
        return ICON_256.to_vec();
    }
    let mut out = vec![0u8; size * size * 4];
    let scale = SRC_SIZE as f32 / size as f32;
    for y in 0..size {
        let sy = ((y as f32 + 0.5) * scale - 0.5).clamp(0.0, (SRC_SIZE - 1) as f32);
        let y0 = sy.floor() as usize;
        let y1 = (y0 + 1).min(SRC_SIZE - 1);
        let fy = sy - y0 as f32;
        for x in 0..size {
            let sx = ((x as f32 + 0.5) * scale - 0.5).clamp(0.0, (SRC_SIZE - 1) as f32);
            let x0 = sx.floor() as usize;
            let x1 = (x0 + 1).min(SRC_SIZE - 1);
            let fx = sx - x0 as f32;

            let idx = |px: usize, py: usize| (py * SRC_SIZE + px) * 4;
            let i00 = idx(x0, y0);
            let i10 = idx(x1, y0);
            let i01 = idx(x0, y1);
            let i11 = idx(x1, y1);

            let w00 = (1.0 - fx) * (1.0 - fy);
            let w10 = fx * (1.0 - fy);
            let w01 = (1.0 - fx) * fy;
            let w11 = fx * fy;

            let dst = (y * size + x) * 4;
            for c in 0..4 {
                let v = ICON_256[i00 + c] as f32 * w00
                    + ICON_256[i10 + c] as f32 * w10
                    + ICON_256[i01 + c] as f32 * w01
                    + ICON_256[i11 + c] as f32 * w11;
                out[dst + c] = v.round().clamp(0.0, 255.0) as u8;
            }
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pixel(img: &[u8], size: usize, x: usize, y: usize) -> [u8; 4] {
        let i = (y * size + x) * 4;
        [img[i], img[i + 1], img[i + 2], img[i + 3]]
    }

    #[test]
    fn corners_are_transparent_and_center_is_opaque() {
        let size = 64;
        let img = render(size);
        assert_eq!(img.len(), size * size * 4);
        assert_eq!(pixel(&img, size, 0, 0)[3], 0, "rounded corner");
        let center = pixel(&img, size, 32, 32);
        assert!(center[3] > 200, "center should be opaque: {center:?}");
    }

    #[test]
    fn renders_every_tray_size() {
        for size in [16, 20, 24, 32, 48, 64, 256] {
            let img = render(size);
            assert!(img.chunks(4).any(|p| p[3] >= 250), "{size}px has opaque pixels");
        }
    }
}
