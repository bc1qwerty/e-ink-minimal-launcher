# E-ink Minimal Launcher

A minimalist, E-ink friendly Android launcher designed for Boox Palma, Leaf 3, Lenovo Y700, and similar devices.

<p align="center">
  <img src="assets/photo.jpg" width="400" alt="Launcher on Device" />
</p>

## Features

- **Minimalist Design**: Clean interface optimized for E-ink displays and tablets.
- **Dynamic Column Grid**: Choose between 1 to 5 columns for favorites (supports 4 to 20 apps) depending on your device's screen size.
- **Niagara-style Index Bar**: "Wave" effect with intense pop-out and large bold fonts for easy navigation.
- **Animation Toggle**: Option to disable wave animations for a ghosting-free experience on low refresh rate E-ink devices like Boox Leaf 3.
- **Smart Indexing**: Only shows letters/consonants that have installed apps.
- **Korean Support**: Full support for Korean consonants (ㄱ, ㄴ, ㄷ...) in the index bar.
- **Drag & Drop**: Long press items in the favorites view to reorder them.
- **Z-Order Optimization**: Index bar always stays on top for best visibility.

## Versioning

- **v1.3.2**: Changed default animation setting to 'off' for better compatibility with low-spec E-ink devices. Added option to enable it in settings.
- **v1.3.1**: Fixed an issue where certain manufacturer-specific apps (like Onyx Launcher) would not open.
- **v1.3.0**: Expanded column layout up to 5 columns (for tablets like Y700) and added an animation toggle setting for E-ink optimization (e.g., Boox Leaf 3).
- **v1.2.1**: Reduced index pop-out intensity and minor UI refinements.
- **v1.2**: Fixed index bar layering (always on top), refined "wave" pop-out visibility.
- **v1.1**: Added column selection (1/2/3), improved index bar aesthetics, and centered favorites layout.
- **v1.0**: Initial stable release.

## Installation

```bash
adb install e-ink-minimal-launcher-v1.3.2.apk
```

## License

MIT License - Copyright (c) 2026 bc1qwerty
