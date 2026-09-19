"""Curated manufacturer facts. Never infer active sensor crops from pixel ratios."""
import json
from pathlib import Path

rows = []
def add(maker, model, sensor, sw, sh, url, modes, sensor_type='CMOS', verified=True):
    rows.append(dict(id=(maker+'-'+model).lower().replace(' ', '-'), manufacturer=maker, model=model,
        sensorName=sensor, sensorWidthMm=sw, sensorHeightMm=sh, sensorType=sensor_type,
        sourceName=maker+' official specifications', sourceUrl=url, verifiedDate='2026-09-07', verified=verified,
        notesEn='Select the exact recording mode. Focal length is entered separately.',
        notesTr='Tam kayıt modunu seçin. Odak uzaklığı ayrıca girilir.',
        modes=[dict(id=str(i), name=n, width=w, height=h, aspectRatio=f'{w}:{h}', activeWidthMm=x, activeHeightMm=y, verified=x is not None and y is not None) for i,(n,w,h,x,y) in enumerate(modes)]))

arri='https://www.arri.com/en/cine-systems/cine-cameras/'
add('ARRI','ALEXA 35','ALEV 4 Super 35',27.99,19.22,arri+'legacy-cine-cameras/alexa-35', [('4.6K Open Gate',4608,3164,27.99,19.22)])
for model, slug in [('ALEXA Mini LF','alexa-mini-lf'),('ALEXA LF','alexa-lf')]:
    add('ARRI',model,'ALEV III Large Format',36.70,25.54,arri+slug,[('4.5K LF Open Gate',4448,3096,36.70,25.54),('4.5K LF 2.39:1',4448,1856,36.70,15.31)])
add('ARRI','ALEXA Mini','ALEV III Super 35',28.25,18.17,arri+'legacy-cine-cameras/alexa-mini',[('3.4K Open Gate ARRIRAW',3424,2202,28.25,18.17),('3.2K ProRes',3200,1800,26.40,14.85),('UHD ProRes',3840,2160,26.40,14.85)])
add('ARRI','ALEXA SXT W','ALEV III Super 35',28.25,18.17,'https://www.arri.com/en/camera-systems/cameras/legacy-camera-systems/alexa-sxt-w',[('3.4K Open Gate ARRIRAW',3424,2202,28.25,18.17),('2.8K 16:9 ARRIRAW',2880,1620,23.76,13.37)])
sony='https://pro.sony/ue_US/products/digital-cinema-cameras/'
add('Sony','VENICE 2 8K','Full Frame 8.6K',35.9,24.0,sony+'venice2',[('8.6K 3:2',8640,5760,35.9,24.0),('8.6K 17:9',8640,4556,35.9,19.0),('5.8K 17:9 S35',5792,3056,24.1,12.7)])
add('Sony','VENICE 2 6K','Full Frame 6K',35.9,24.0,sony+'venice2',[('6K 3:2',6048,4032,35.9,24.0),('4K 17:9 S35',4096,2160,24.3,12.8)])
add('Sony','BURANO','Full Frame 8.6K',35.9,20.2,'https://pro.sony/en_GB/products/digital-cinema-cameras/burano',[('FF 8.6K 16:9 XAVC',7680,4320,35.9,20.2),('FF 8.6K 17:9 XAVC',8192,4320,35.9,18.9),('FFc 6K 16:9 XAVC',3840,2160,33.6,18.9)])
add('Sony','FX3','Exmor R Full Frame',35.6,23.8,'https://www.sony.com/electronics/support/camcorders-and-video-cameras-interchangeable-lens-camcorders/ilme-fx3/specifications',[('UHD',3840,2160,None,None)])
canon='https://www.canon-europe.com/'
add('Canon','EOS C400','Full Frame BSI',36.0,19.0,canon+'cameras/eos-c400/specifications/',[('6K RAW Full Frame',6000,3164,36.0,19.0),('DCI 4K Full Frame',4096,2160,36.0,19.0),('UHD Full Frame',3840,2160,33.8,19.0)])
add('Canon','EOS C500 Mark II','Full Frame',38.1,20.1,canon+'video-cameras/eos-c500-mark-ii/specifications/',[('5.9K RAW Full Frame',5952,3140,38.1,20.1),('DCI 4K Full Frame',4096,2160,38.1,20.1),('UHD Full Frame',3840,2160,35.7,20.1)])
for model,slug in [('EOS C300 Mark III','eos-c300-mark-iii'),('EOS C70','eos-c70')]:
    add('Canon',model,'Super 35 DGO',26.2,13.8,canon+'video-cameras/'+slug+'/specifications/',[('DCI 4K Super 35',4096,2160,26.2,13.8),('UHD Super 35',3840,2160,24.6,13.8)])
add('RED','V-RAPTOR 8K VV','VV',40.96,21.6,'https://docs.red.com/955-0199/955-0199_V1.0_Rev_A%20RED_PS_V-RAPTOR_Operation_Guide/Content/4_Menus/b_ProjSet/Format/All_Formats.htm',[('8K 17:9',8192,4320,40.96,21.6)])
add('RED','KOMODO','Super 35 Global Shutter',27.03,14.26,'https://docs.red.com/955-0190_v1.3/955-0190_v1.3_REV-1.3_RED_PS_KOMODO_Operation_Guide/Content/4_Menus/ProjSet/Format.htm',[('6K 17:9',6144,3240,27.03,14.26),('6K 2.4:1',6144,2592,27.03,11.40)])
add('RED','KOMODO-X','Super 35 Global Shutter',27.03,14.26,'https://docs.red.com/955-0219/955-0219_V1.0%20Rev-B%20RED%20PS%2C%20KOMODO-X%20Operation%20Guide%20HTML/Content/A_TechSpecs/Specs_KOMODO-X.htm',[('6K 17:9',6144,3240,27.03,14.26)])
bmd='https://www.blackmagicdesign.com/products/'
add('Blackmagic Design','PYXIS 6K','Full Frame',36.0,24.0,bmd+'blackmagicpyxis/techspecs',[('6K Open Gate',6048,4032,36.0,24.0)])
add('Blackmagic Design','PYXIS 12K','RGBW Large Format',35.64,23.32,bmd+'blackmagicpyxis/techspecs',[('12K Open Gate',12288,8040,35.64,23.32)])
add('Blackmagic Design','URSA Mini Pro 12K','RGBW Super 35',27.03,14.25,bmd+'blackmagicursaminipro/techspecs',[('12K DCI',12288,6480,27.03,14.25)])
add('Blackmagic Design','URSA Mini Pro 4.6K G2','Super 35',25.34,14.25,bmd+'blackmagicursaminipro/techspecs',[('4.6K',4608,2592,25.34,14.25)])
for model in ['Pocket Cinema Camera 6K Pro','Pocket Cinema Camera 6K G2']:
    add('Blackmagic Design',model,'Super 35',23.10,12.99,bmd+'blackmagicpocketcinemacamera/techspecs',[('6K',6144,3456,23.10,12.99)])
add('Blackmagic Design','Pocket Cinema Camera 4K','Four Thirds',18.96,10.0,bmd+'blackmagicpocketcinemacamera/techspecs',[('4K DCI',4096,2160,18.96,10.0)])
add('Panasonic','AU-EVA1','Super 35 5.7K',24.60,12.97,'https://pro-av.panasonic.net/en/cinema_camera_varicam_eva/products/eva1/',[('5.7K sensor / DCI 4K output',4096,2160,24.60,12.97)])
add('Panasonic','LUMIX S1H','Full Frame',35.6,23.8,'https://www.panasonic.com/in/consumer/cameras-camcorders/camera/s/dc-s1h.specs.html',[('6K 3:2',5952,3968,None,None)])

add('ARRI','ALEXA XT','ALEV III Super 35',28.17,18.13,'https://www.arri.com/resource/blob/178012/d6a32bdbaee788486bce45ec1de9e4f1/alexa-classic-and-xt-recording-areas-surround-views-framelines-sup-11-data.pdf',[('Open Gate ARRIRAW',3414,2198,28.17,18.13),('4:3 ARRIRAW',2880,2160,23.76,17.82)])
add('Sony','VENICE','Full Frame 6K',35.9,24.0,'https://pro.sony/en_GB/products/digital-cinema-cameras/venice',[('6K 3:2',6048,4032,35.9,24.0),('4K 17:9 S35',4096,2160,24.3,12.8)])
add('Sony','FX6','Full Frame',None,None,'https://pro.sony/ue_US/products/handheld-camcorders/ilme-fx6',[('UHD',3840,2160,None,None)])
add('Sony','FX9','Full Frame 6K',None,None,'https://pro.sony/s3/2024/02/12095925/Sony_Cinema_Line_Chart_2024.pdf',[('UHD',3840,2160,None,None)])
add('Canon','EOS C80','Full Frame BSI',36.0,19.0,'https://www.canon.ca/dam/products/BUSINESS-UNIT/ITCG/Video-Cameras/Cinema-EOS-Cameras/EOS-C80/EOS-C80-Specifications.pdf',[('6K RAW Full Frame',6000,3164,36.0,19.0),('DCI 4K Full Frame',4096,2160,36.0,19.0),('UHD Full Frame',3840,2160,33.8,19.0)])
add('RED','V-RAPTOR [X] 8K VV','VV Global Shutter',40.96,21.60,'https://docs.red.com/955-0225/955-0225_V2.0%20Rev-A%20RED%20PS%2C%20V-RAPTOR%20%5BX%5D%208K%20VV%20Operation%20Guide/Content/4_Menus/b_ProjSet/Format/VV_8K.htm',[('8K 17:9',8192,4320,40.96,21.60),('8K 16:9',7680,4320,38.40,21.60),('8K 2.4:1',8192,3456,40.96,17.28)])
add('RED','V-RAPTOR XL 8K VV','VV',40.96,21.60,'https://docs.red.com/955-0203/955-0203_V1.7%20Rev-B%20RED%20PS%2C%20V-RAPTOR%20XL%208K%20VV%20Operation%20Guide%20HTML/Content/4_Menus/b_ProjSet/Format/All_Formats.htm',[('8K 17:9',8192,4320,40.96,21.60)])
add('Blackmagic Design','Cinema Camera 6K','Full Frame',36.0,24.0,bmd+'blackmagiccinemacamera/techspecs',[('6K Open Gate',6048,4032,36.0,24.0)])
add('Blackmagic Design','URSA Cine 12K LF','RGBW Large Format',35.64,23.32,'https://www.blackmagicdesign.com/mx/products/blackmagicursacine/techspecs',[('12K Open Gate',12288,8040,35.64,23.32)])
add('Panasonic','LUMIX BS1H','Full Frame',35.6,23.8,'https://www.panasonic.com/in/consumer/cameras-camcorders/camera/lumix-box-style-cameras/dc-bs1h.specs.html',[('6K 3:2',5952,3968,None,None)])
add('Panasonic','VariCam LT','Super 35 MOS',None,None,'https://pro-av.panasonic.net/manual/html/VARICAM_LT%28VQT5M58A-10%28E%29%29/chapter13_01_02.htm',[('DCI 4K',4096,2160,None,None)])
add('Panasonic','VariCam 35','Super 35 MOS',None,None,'https://pro-av.panasonic.net/en/cinema_camera_varicam_eva/products/',[('DCI 4K',4096,2160,None,None)])

if __name__ == '__main__':
    target=Path(__file__).resolve().parents[1]/'app/src/main/assets/camera_presets.json'
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'{len(rows)} cameras, {sum(len(r["modes"]) for r in rows)} modes')
