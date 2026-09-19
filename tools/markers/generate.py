"""Generate exact-size ArUco / modern ChArUco PNG + PDF print targets.
pip install -r requirements.txt
python generate.py marker --id 12 --size-mm 150 --out marker12
python generate.py charuco --squares-x 5 --squares-y 7 --square-mm 40 --marker-mm 30 --out board
"""
import argparse
from pathlib import Path
import cv2
from PIL import Image
from reportlab.pdfgen import canvas
from reportlab.lib.utils import ImageReader
from reportlab.lib.units import mm


def dictionary(name):
    if not name.startswith("DICT_") or not hasattr(cv2.aruco, name):
        raise ValueError(f"Unknown dictionary: {name}")
    return cv2.aruco.getPredefinedDictionary(getattr(cv2.aruco, name))


def create(args):
    d = dictionary(args.dictionary)
    if args.kind == "marker":
        if not 0 <= args.id < len(d.bytesList):
            raise ValueError("Marker ID outside dictionary")
        if args.size_mm <= 0:
            raise ValueError("Physical size must be positive")
        width = height = args.size_mm
        image = cv2.aruco.generateImageMarker(d, args.id, max(256, round(width / 25.4 * args.dpi)))
        title = f"{args.dictionary} ID {args.id} | black square {width:g} mm"
    else:
        if not 0 < args.marker_mm < args.square_mm or min(args.squares_x, args.squares_y) < 3:
            raise ValueError("Board requires >=3 squares and 0 < marker mm < square mm")
        width, height = args.squares_x * args.square_mm, args.squares_y * args.square_mm
        board = cv2.aruco.CharucoBoard((args.squares_x, args.squares_y), args.square_mm / 1000, args.marker_mm / 1000, d)
        # Modern pattern (OpenCV >= 4.6), same as Android CharucoBoard.
        image = board.generateImage((round(width / 25.4 * args.dpi), round(height / 25.4 * args.dpi)), marginSize=0, borderBits=1)
        title = f"ChArUco {args.squares_x}x{args.squares_y} | square {args.square_mm:g} mm | marker {args.marker_mm:g} mm"
    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    pil = Image.fromarray(image)
    pil.save(out.with_suffix(".png"), dpi=(args.dpi, args.dpi))
    # MediaBox grows for oversized boards: never silently fit to A4.
    margin = 15
    page_width, page_height = max(210, width + 2 * margin), max(297, height + 2 * margin + 18)
    pdf = canvas.Canvas(str(out.with_suffix(".pdf")), pagesize=(page_width * mm, page_height * mm))
    x, y = (page_width - width) / 2, (page_height - height) / 2
    pdf.drawImage(ImageReader(pil), x * mm, y * mm, width=width * mm, height=height * mm, mask='auto')
    pdf.setFont("Helvetica", 9)
    pdf.drawString(10 * mm, 10 * mm, title)
    pdf.drawString(10 * mm, 6 * mm, "Print 100% / Actual Size. Disable Fit. Measure black square / board grid after printing.")
    pdf.showPage()
    pdf.save()
    print(f"Created {out.with_suffix('.png')} and {out.with_suffix('.pdf')}; physical image {width:g} x {height:g} mm; page {page_width:g} x {page_height:g} mm")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("kind", choices=("marker", "charuco"))
    parser.add_argument("--dictionary", default="DICT_4X4_50")
    parser.add_argument("--id", type=int, default=12)
    parser.add_argument("--size-mm", type=float, default=150)
    parser.add_argument("--squares-x", type=int, default=5)
    parser.add_argument("--squares-y", type=int, default=7)
    parser.add_argument("--square-mm", type=float, default=40)
    parser.add_argument("--marker-mm", type=float, default=30)
    parser.add_argument("--dpi", type=int, default=300)
    parser.add_argument("--out", default="marker")
    args = parser.parse_args()
    if not 72 <= args.dpi <= 1200:
        parser.error("DPI must be between 72 and 1200")
    create(args)


if __name__ == "__main__":
    main()
