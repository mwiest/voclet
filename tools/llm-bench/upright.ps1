# Rotates an image by a quarter turn and saves it. Called by ocrbench once
# Tesseract's OSD has said which way up the page is; a photo taken with the
# tablet held sideways carries no EXIF orientation tag to reveal that.
#
#   powershell -File upright.ps1 -Path in.jpg -Dest out.jpg -Degrees 270
param(
  [Parameter(Mandatory = $true)][string]$Path,
  [Parameter(Mandatory = $true)][string]$Dest,
  [Parameter(Mandatory = $true)][int]$Degrees
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$img = [System.Drawing.Image]::FromFile((Resolve-Path $Path))
$flip = switch ($Degrees) {
  90  { [System.Drawing.RotateFlipType]::Rotate90FlipNone }
  180 { [System.Drawing.RotateFlipType]::Rotate180FlipNone }
  270 { [System.Drawing.RotateFlipType]::Rotate270FlipNone }
  default { [System.Drawing.RotateFlipType]::RotateNoneFlipNone }
}
$img.RotateFlip($flip)

$codec = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$params = New-Object System.Drawing.Imaging.EncoderParameters 1
$params.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality, 92L)
$img.Save($Dest, $codec, $params)
"$($img.Width) x $($img.Height)"
