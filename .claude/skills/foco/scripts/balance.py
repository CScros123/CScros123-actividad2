"""Equilibrio de llaves y paréntesis (sin comentarios ni cadenas). Uso: python3 -I balance.py <archivos>"""
import re,sys
for f in sys.argv[1:]:
    s=open(f).read()
    t=re.sub(r'//[^\n]*','',s)
    t=re.sub(r'/\*.*?\*/','',t,flags=re.S)
    t=re.sub(r'"(\\.|[^"\\\n])*"','""',t)
    t=re.sub(r"'(\\.|[^'\\\n])'","''",t)
    print(f, t.count('{')-t.count('}'), t.count('(')-t.count(')'))
