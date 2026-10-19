# Image Simplifier

A small Java command line tool that makes PNG files smaller by merging similar colors into one shared color (**color quantization**).

Fewer distinct colors means the image can be saved as an indexed PNG, which often cuts the file size dramatically.

![Before and after](example.jpg)

## How it works

1. **Count colors:** every pixel color is counted, most frequent colors first.
2. **Build a palette:** a color is only added to the palette if it is further away from all existing palette colors than the chosen tolerance. Distance is weighted the way the human eye sees color (green 59 %, red 30 %, blue 11 %).
3. **Map pixels:** every pixel is replaced by its closest palette color.
4. **Save:** with 256 colors or fewer the result is written as an indexed PNG, which saves the most space.
5. **Report:** prints image size, number of colors before and after, and the space saved.

## Usage

```bash
javac ImageSimplifier.java
java ImageSimplifier <input> <output.png> [tolerance]

# example
java ImageSimplifier graphic.png small.png 32
```

```
Image:     200 x 100 pixels
Tolerance: 32
Colors:    20000  ->  22
File size: 56,296 bytes  ->  1,239 bytes  (97.8 % saved)
Saved to:  small.png
```

`tolerance` is the allowed color distance (0 to 255, default 32):

| Tolerance | Result |
|---|---|
| small | many colors, barely visible difference, small savings |
| large | few colors, poster effect, big savings |

Works best for graphics, screenshots and illustrations. Photos saved as JPG are already heavily compressed, so with a small tolerance the PNG can end up larger. The tool tells you when that happens.

Invalid input (missing file, tolerance that is not a number or outside 0 to 255) is reported with a clear message instead of a crash.

## Built with

Java (standard library only: `javax.imageio`, `java.awt.image`)
