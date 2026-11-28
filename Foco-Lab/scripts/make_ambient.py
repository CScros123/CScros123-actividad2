"""Ambientes originales para la pausa de Foco (sin muestras ni descargas).
Genera res/raw/ambiente_lluvia_suave.ogg, ambiente_noche.ogg y ambiente_piano.ogg.
Requiere numpy y ffmpeg en el PC, nunca en el teléfono. Cada pista es circular (loop sin cortes).
"""
from pathlib import Path
import subprocess, wave
import numpy as np

RATE=22050
ROOT=Path(__file__).resolve().parents[1]

def tone(note,seconds,decay=1.4,bright=.2):
    t=np.arange(round(seconds*RATE))/RATE
    f=440*2**((note-69)/12)
    env=(1-np.exp(-t*40))*np.exp(-t/decay)
    return env*(np.sin(2*np.pi*f*t)+bright*np.sin(4*np.pi*f*t)*np.exp(-t*2))

def pad(note,seconds):
    t=np.arange(round(seconds*RATE))/RATE
    f=440*2**((note-69)/12)
    env=np.sin(np.pi*t/seconds)**2
    return env*(np.sin(2*np.pi*f*t)+.5*np.sin(2*np.pi*f*1.003*t))

def render(name,seconds,seed,build):
    n=round(seconds*RATE);song=np.zeros((n,2));rng=np.random.default_rng(seed)
    def add(s,when,level=1,pan=0):
        idx=(np.arange(len(s))+round(when*RATE))%n
        song[idx,0]+=s*level*(1-pan*.4);song[idx,1]+=s*level*(1+pan*.4)
    build(add,rng,n,song)
    for c in range(2):
        s=song[:,c];song[:,c]=sum(np.roll(s,i) for i in range(4))/4
    song=np.tanh(song*1.5);song*=.6/max(.6,np.max(np.abs(song)))
    wav=ROOT/f'scripts/{name}.wav'
    with wave.open(str(wav),'wb') as f:
        f.setnchannels(2);f.setsampwidth(2);f.setframerate(RATE);f.writeframes((song*32767).astype('<i2').tobytes())
    out=ROOT/f'app/src/main/res/raw/{name}.ogg'
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(wav),'-c:a','libvorbis','-q:a','1',str(out)],check=True)
    wav.unlink();print(out,out.stat().st_size)

def rain(add,rng,n,song):
    noise=rng.normal(0,1,(n,2))
    for c in range(2):  # lluvia: ruido filtrado circular
        x=noise[:,c]
        for k in (9,9):x=np.real(np.fft.ifft(np.fft.fft(x)*np.fft.fft(np.ones(k)/k,n)))
        song[:,c]+=x*.05
    for _ in range(900):  # gotas
        t=np.arange(round(.03*RATE))/RATE
        add(rng.normal(0,1,len(t))*np.exp(-t*160),rng.uniform(0,n/RATE),.01,rng.uniform(-1,1))
    chords=[[50,57,62,65],[46,53,58,62],[48,55,60,64],[45,52,57,60]]
    for i in range(16):
        for j,note in enumerate(chords[i%4]):add(pad(note,11),i*n/RATE/16,.03,(j-1.5)/2)

def night(add,rng,n,song):
    chords=[[45,52,57,60],[41,48,53,57],[43,50,55,59],[40,47,52,55]]
    for i in range(12):
        for j,note in enumerate(chords[i%4]):add(pad(note-12 if j==0 else note,15),i*n/RATE/12,.035,(j-1.5)/2)
    for _ in range(140):  # grillos lejanos
        t=np.arange(round(.12*RATE))/RATE
        add(np.sin(2*np.pi*4200*t)*np.sin(np.pi*t/.12)*(np.sin(2*np.pi*30*t)>0),rng.uniform(0,n/RATE),.004,rng.uniform(-1,1))
    for k in range(24):add(tone(rng.choice([69,72,76,79]),4,2.5,.1),k*n/RATE/24+3,.02,.3)

def piano(add,rng,n,song):
    beat=60/60;chords=[[48,55,60,64],[45,52,57,64],[41,48,53,57],[43,50,55,62]]
    for bar in range(int(n/RATE/(4*beat))):
        ch=chords[bar%4]
        for b,note in enumerate(ch):add(tone(note+12,4,1.6),(bar*4+b)*beat,.05,(b-1.5)/2)
        add(tone(ch[0]-12,4,2.2,.05),bar*4*beat,.06)
        if bar%2:add(tone(rng.choice([72,74,76,79]),3,1.2),(bar*4+2.5)*beat,.035,.3)

render('ambiente_lluvia_suave',150,7,rain)
render('ambiente_noche',150,11,night)
render('ambiente_piano',144,23,piano)
