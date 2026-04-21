#!/usr/bin/env python3
import json
from pathlib import Path

def main():
    p=Path(r"e:\Web\Nemo_web\Android project\Nemo\app\src\main\assets\grammar\questions")
    total=0
    for fp in sorted(p.glob('N*.json')):
        with fp.open('r',encoding='utf-8') as f:
            data=json.load(f)
        print(fp.name, len(data))
        total+=len(data)
    print('Total', total)

if __name__=='__main__':
    main()
