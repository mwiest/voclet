# Document preprocessing for a photographed page: grayscale, upscale, and
# Sauvola local binarization. What a modern recognizer does internally and what
# Tesseract expects to be handed.
#
#   powershell -File preprocess.ps1 -Path in.jpg -Dest out.png [-Scale 2] [-K 0.2]
#
# Deliberately no image library. The arithmetic is a C# snippet compiled by
# Add-Type at run time, because the same three steps are ~150 lines of plain
# Kotlin over a Bitmap on Android - no OpenCV, which is FOSS but a 40 MB native
# dependency for a project that dropped Firebase to stay F-Droid-friendly.
#
# Sauvola rather than a global Otsu threshold: a photographed page is lit
# unevenly and curves away at the spine, so one threshold for the whole image
# either fills the shadowed side with ink or erases the bright side. Sauvola
# compares each pixel with the mean and variance of its own neighbourhood,
#   T = mean * (1 + k * (stddev / 128 - 1)),
# which is computed in one pass here from integral images.
param(
  [Parameter(Mandatory = $true)][string]$Path,
  [Parameter(Mandatory = $true)][string]$Dest,
  [double]$Scale = 2.0,
  [double]$K = 0.2,
  [int]$Window = 0
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Imaging;

public static class Doc {
    public static void Run(string src, string dest, double scale, double k, int window) {
        using (var original = new Bitmap(src)) {
            int w = (int)Math.Round(original.Width * scale);
            int h = (int)Math.Round(original.Height * scale);

            // Grayscale and resample in one step: draw the source into a bigger
            // surface, then read the luminance out of it.
            byte[] gray = new byte[w * h];
            using (var scaled = new Bitmap(w, h, PixelFormat.Format32bppArgb)) {
                using (var g = Graphics.FromImage(scaled)) {
                    g.InterpolationMode = System.Drawing.Drawing2D.InterpolationMode.HighQualityBicubic;
                    g.DrawImage(original, 0, 0, w, h);
                }
                var data = scaled.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.ReadOnly, PixelFormat.Format32bppArgb);
                byte[] pixels = new byte[Math.Abs(data.Stride) * h];
                System.Runtime.InteropServices.Marshal.Copy(data.Scan0, pixels, 0, pixels.Length);
                scaled.UnlockBits(data);
                for (int y = 0; y < h; y++) {
                    int row = y * data.Stride;
                    for (int x = 0; x < w; x++) {
                        int p = row + x * 4;
                        gray[y * w + x] = (byte)((pixels[p + 2] * 299 + pixels[p + 1] * 587 + pixels[p] * 114) / 1000);
                    }
                }
            }

            if (window <= 0) window = Math.Max(15, h / 40);
            if (window % 2 == 0) window++;

            // Integral images of the values and of their squares, so the mean
            // and variance of any window cost four lookups each.
            long[] sum = new long[(w + 1) * (h + 1)];
            long[] sumSq = new long[(w + 1) * (h + 1)];
            for (int y = 1; y <= h; y++) {
                long rowSum = 0, rowSqSum = 0;
                for (int x = 1; x <= w; x++) {
                    long v = gray[(y - 1) * w + (x - 1)];
                    rowSum += v; rowSqSum += v * v;
                    sum[y * (w + 1) + x] = sum[(y - 1) * (w + 1) + x] + rowSum;
                    sumSq[y * (w + 1) + x] = sumSq[(y - 1) * (w + 1) + x] + rowSqSum;
                }
            }

            int half = window / 2;
            using (var outBmp = new Bitmap(w, h, PixelFormat.Format24bppRgb)) {
                var outData = outBmp.LockBits(new Rectangle(0, 0, w, h), ImageLockMode.WriteOnly, PixelFormat.Format24bppRgb);
                byte[] outPixels = new byte[Math.Abs(outData.Stride) * h];
                for (int y = 0; y < h; y++) {
                    int y0 = Math.Max(0, y - half), y1 = Math.Min(h - 1, y + half);
                    for (int x = 0; x < w; x++) {
                        int x0 = Math.Max(0, x - half), x1 = Math.Min(w - 1, x + half);
                        long area = (long)(x1 - x0 + 1) * (y1 - y0 + 1);
                        long s = sum[(y1 + 1) * (w + 1) + (x1 + 1)] - sum[y0 * (w + 1) + (x1 + 1)]
                               - sum[(y1 + 1) * (w + 1) + x0] + sum[y0 * (w + 1) + x0];
                        long sq = sumSq[(y1 + 1) * (w + 1) + (x1 + 1)] - sumSq[y0 * (w + 1) + (x1 + 1)]
                                - sumSq[(y1 + 1) * (w + 1) + x0] + sumSq[y0 * (w + 1) + x0];
                        double mean = (double)s / area;
                        double variance = (double)sq / area - mean * mean;
                        double sd = variance > 0 ? Math.Sqrt(variance) : 0;
                        double t = mean * (1.0 + k * (sd / 128.0 - 1.0));
                        byte value = gray[y * w + x] > t ? (byte)255 : (byte)0;
                        int p = y * outData.Stride + x * 3;
                        outPixels[p] = value; outPixels[p + 1] = value; outPixels[p + 2] = value;
                    }
                }
                System.Runtime.InteropServices.Marshal.Copy(outPixels, 0, outData.Scan0, outPixels.Length);
                outBmp.UnlockBits(outData);
                outBmp.Save(dest, ImageFormat.Png);
                Console.WriteLine(w + " x " + h + ", window " + window);
            }
        }
    }
}
"@

[Doc]::Run((Resolve-Path $Path).Path, $Dest, $Scale, $K, $Window)
