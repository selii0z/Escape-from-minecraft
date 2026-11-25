#!/usr/bin/env python3
"""Import only the five medical models from the user's EFTPack; no Fabric code."""
import argparse, io, json, pathlib, zipfile
MODELS = {
    'salewa': ('salewa', 'texturealewa', '0'),
    'bandage': ('army_bandage', 'texturermyandage', '0'),
    'splint': ('splint', 'textureplint', '0'),
    'hemostat': ('calok-b_hemostatic_applicator', 'alok', '0'),
    'medkit': ('ifak', 'texture', '1'),
}
def create_overlay(pack, dest):
    with zipfile.ZipFile(pack) as archive:
        raw = archive.read('overrides/mods/eft-mod-1.21.1.jar')
    with zipfile.ZipFile(io.BytesIO(raw)) as jar, zipfile.ZipFile(dest, 'w', zipfile.ZIP_DEFLATED) as out:
        metadata=json.loads(jar.read('fabric.mod.json'))
        if metadata.get('id')!='eftmod' or metadata.get('license')!='CC0-1.0':
            raise ValueError('Unexpected source or declared license')
        out.writestr('META-INF/eft-medical-LICENSE.txt',jar.read('LICENSE_eft-mod'))
        out.writestr('META-INF/eft-medical-NOTICE.txt','Source: user-supplied EFTPack-1.21.1.mrpack / EFT Client by ShiftingKill; declares CC0-1.0. Models credited to Blockbench in original files. Third-party rights not independently verified. Only five item models and their textures; no code or sounds imported.\n')
        for target,(source,texture,key) in MODELS.items():
            data=json.loads(jar.read('assets/minecraft/models/item/custom/meds/'+source+'.json'))
            data={k:v for k,v in data.items() if k in ('credit','elements','display','ambientocclusion','gui_light')}
            data['textures']={key:'taczoperations:item/eft_medical/'+target,'particle':'taczoperations:item/eft_medical/'+target}
            for element in data['elements']:
                for face in element.get('faces',{}).values():
                    if face.get('texture')!='#'+key:
                        raise ValueError('Unexpected texture reference '+str(face))
            out.writestr('assets/taczoperations/models/item/'+target+'.json',json.dumps(data,separators=(',',':')))
            out.writestr('assets/taczoperations/textures/item/eft_medical/'+target+'.png',jar.read('assets/minecraft/textures/custom/meds/'+texture+'.png'))
    with zipfile.ZipFile(dest) as check:
        assert check.testzip() is None and len(check.namelist())==12
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('mrpack',type=pathlib.Path);parser.add_argument('output',type=pathlib.Path);args=parser.parse_args();create_overlay(args.mrpack,args.output)
