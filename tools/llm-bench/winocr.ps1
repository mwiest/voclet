# Word-level OCR via the OCR engine built into Windows, as a stand-in for a
# classical on-device recognizer (Tesseract on Android). Emits one TSV row per
# word - text, x, y, w, h - because the point of the exercise is the geometry:
# a vocabulary page is two columns, and column membership is an x-coordinate,
# not something a language model needs to infer.
#
#   powershell -NoProfile -ExecutionPolicy Bypass -File winocr.ps1 -Path page.jpg
param([Parameter(Mandatory = $true)][string]$Path)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.WindowsRuntime

# PowerShell 5.1 cannot await a WinRT IAsyncOperation on its own; this is the
# standard reflection shim for it.
$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
    $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and
    $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
})[0]
function Await($op, $type) {
  $task = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
  $task.Wait(-1) | Out-Null
  $task.Result
}

[Windows.Storage.StorageFile, Windows.Storage, ContentType = WindowsRuntime] | Out-Null
[Windows.Graphics.Imaging.BitmapDecoder, Windows.Graphics.Imaging, ContentType = WindowsRuntime] | Out-Null
[Windows.Media.Ocr.OcrEngine, Windows.Media.Ocr, ContentType = WindowsRuntime] | Out-Null

$engine = [Windows.Media.Ocr.OcrEngine]::TryCreateFromUserProfileLanguages()
if (-not $engine) { throw 'no OCR language pack installed' }
[Console]::Error.WriteLine("engine language: $($engine.RecognizerLanguage.LanguageTag)")

$file = Await ([Windows.Storage.StorageFile]::GetFileFromPathAsync((Resolve-Path $Path))) ([Windows.Storage.StorageFile])
$stream = Await ($file.OpenAsync([Windows.Storage.FileAccessMode]::Read)) ([Windows.Storage.Streams.IRandomAccessStream])
$decoder = Await ([Windows.Graphics.Imaging.BitmapDecoder]::CreateAsync($stream)) ([Windows.Graphics.Imaging.BitmapDecoder])
$bitmap = Await ($decoder.GetSoftwareBitmapAsync()) ([Windows.Graphics.Imaging.SoftwareBitmap])
$result = Await ($engine.RecognizeAsync($bitmap)) ([Windows.Media.Ocr.OcrResult])

$out = New-Object System.Text.StringBuilder
foreach ($line in $result.Lines) {
  foreach ($word in $line.Words) {
    $r = $word.BoundingRect
    [void]$out.AppendLine(("{0}`t{1:F0}`t{2:F0}`t{3:F0}`t{4:F0}" -f $word.Text, $r.X, $r.Y, $r.Width, $r.Height))
  }
}
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::Out.Write($out.ToString())
