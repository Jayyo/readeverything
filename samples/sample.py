"""Group the inventory by room and print a packing list."""
import csv
from collections import defaultdict


def packing_list(path: str) -> dict[str, list[str]]:
    rooms: dict[str, list[str]] = defaultdict(list)
    with open(path, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            rooms[row["room"]].append(f'{row["qty"]} x {row["item"]}')
    return dict(rooms)


if __name__ == "__main__":
    for room, items in packing_list("sample.csv").items():
        print(f"{room}: {', '.join(items)}")
