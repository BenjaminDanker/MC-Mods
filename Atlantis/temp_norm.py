import json
raw = "'{\\n  \\\"mobId\\\": \\\"minecraft:zombie\\\"}'"
trim = raw.strip()
mutated = trim != raw
if len(trim) >= 2 and trim[0] == "'" and trim[-1] == "'":
    trim = trim[1:-1]
    mutated = True
mapping = {'n':'\\n','r':'\\r','t':'\\t','\\':'\\\\','\"':'\"','\'':'\''}
builder = []
escaping = False
for ch in trim:
    if escaping:
        if ch in mapping:
            builder.append(mapping[ch])
        else:
            builder.append('\\\\' + ch)
        escaping = False
    elif ch == '\\':
        escaping = True
    else:
        builder.append(ch)
if escaping:
    builder.append('\\\\')
normalized = ''.join(builder)
print('normalized:', repr(normalized))
print(json.loads(normalized))
