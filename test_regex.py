import re
pattern = re.compile(r"\s*/\s*(?![^<]*>|\s*<)")
s = "Line 1 / <br/> Line 2"
print(f"Original: '{s}'")
print(f"Result: '{pattern.sub('<br/>', s)}'")
print(f"Result 2: '{pattern.sub('<br/>', 'Line 1 / Line 2')}'")
