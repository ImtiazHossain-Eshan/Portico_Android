# Portico test assets

All names, addresses, figures, registry references, signatures, and legal terms in this pack are fictional. The PDFs are visibly marked **SAMPLE / NOT LEGALLY VALID**.

## PDF reader files

| File | Portico category | Reader behavior to test |
|---|---|---|
| `01-sample-property-management-contract.pdf` | Contracts | Three pages, dense text, table, signatures, page navigation |
| `02-sample-title-deed.pdf` | Deeds | Two pages, compact legal-style fields and text selection |
| `03-sample-residential-lease.pdf` | Leases | Three pages, recurring charges, clauses, checklist |
| `04-sample-property-tax-statement.pdf` | Taxes | One page, amounts, percentages, negative value, search and zoom |
| `05-sample-condition-inspection-landscape.pdf` | Other | Two landscape pages, rotation, wide tables and status colors |

The PDFs are in `pdf/`.

## Property photos

| File | Suggested use |
|---|---|
| `01-modern-apartment-exterior.png` | Apartment or multi-family building |
| `02-lakeside-duplex.png` | Duplex |
| `03-renovated-brick-townhouse.png` | Townhouse or narrow urban house |
| `04-studio-interior.png` | Studio apartment interior |

The photos are in `property-photos/`. They deliberately include landscape and portrait orientations to test cropping and responsive image display.

## Quick test flow

1. Open a property and choose **Documents**.
2. Upload one PDF into each matching category.
3. Open every file, move between pages, zoom, rotate the landscape report, close it, and reopen it.
4. Confirm the filename, category, property association, upload state, and deletion flow remain correct.
5. Edit or create a property and choose each photo. Confirm the preview crop, saved image, list thumbnail, property detail image, and tablet layout.

