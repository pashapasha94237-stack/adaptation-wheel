$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$releaseRoot=$PSScriptRoot
$releaseName='adaptation-wheel-1.20.1-4.49.1'
$releaseDirectory=Join-Path $releaseRoot 'dist'
New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
function Read-Entry($archive,[string]$name){
    $entry=$archive.GetEntry($name);if(!$entry){throw "Missing runtime file: $name"}
    $reader=[IO.StreamReader]::new($entry.Open())
    try{return $reader.ReadToEnd()}finally{$reader.Dispose()}
}
function Entry-Hash($entry){
    $stream=$entry.Open();$sha=[Security.Cryptography.SHA256]::Create()
    try{return [BitConverter]::ToString($sha.ComputeHash($stream)).Replace('-','').ToLower()}finally{$stream.Dispose();$sha.Dispose()}
}
$outputs=@();$baselineClasses=$null
foreach($variant in @('trained','untrained','trained-online','untrained-online')){
    $fileName=$releaseName+'-'+$variant+'.jar'
    $builtJar=Join-Path $releaseRoot ('build/libs/'+$fileName)
    $archive=[IO.Compression.ZipFile]::OpenRead($builtJar)
    try{
        $capability=Read-Entry $archive 'META-INF/adaptation-analysis.properties'
        $expected=if($variant.EndsWith('-online')){'online=true'}else{'online=false'}
        if(!$capability.Contains($expected)){throw "Incorrect build capability: $variant"}
        $trained=!$variant.StartsWith('untrained')
        $marker=Read-Entry $archive 'META-INF/adaptation-learning.properties'
        if(!$marker.Contains('pretrained='+$trained.ToString().ToLower())){throw 'Incorrect prepared knowledge marker'}
        $seedEntry=$archive.GetEntry('knowledge/pretrained-v1.json')
        if($trained){
            $knowledge=(Read-Entry $archive 'knowledge/pretrained-v1.json')|ConvertFrom-Json
            if($knowledge.schema -ne 1 -or $knowledge.facts.Count -ne 506){throw 'Incorrect prepared knowledge dataset'}
            if(@($knowledge.facts|Where-Object{$_.confidence -gt 80 -or !$_.source.StartsWith('seed:')}).Count){throw 'Prepared knowledge contains unreviewed high-confidence facts'}
        }elseif($seedEntry){throw 'Untrained JAR contains prepared knowledge'}
        if(!(Read-Entry $archive 'META-INF/mods.toml').Contains('version="4.49.1-'+$variant+'"')){throw 'Incorrect mod version'}
        if((Read-Entry $archive 'META-INF/jarjar/metadata.json') -match 'distant'){throw 'Distant Horizons is still listed in jar-in-jar metadata'}
        foreach($entry in $archive.Entries){if($entry.FullName -match '(?i)distant-horizons|distanthorizons|LandscapeMemory|LandscapeServerPolicy'){throw "Removed landscape component still in the jar: $($entry.FullName)"}}
        foreach($lang in @('ru_ru','en_us')){if((Read-Entry $archive ('assets/adaptationwheel/lang/'+$lang+'.json')) -match '"meta.adaptationwheel.(landscape|config.landscape|config.archive)'){throw "Landscape memory texts still in $lang"}}
        if(!(Read-Entry $archive 'META-INF/LICENSE').StartsWith('MIT License')){throw 'Missing MIT licence'}
        if(!(Read-Entry $archive 'META-INF/THIRD-PARTY.md').Contains('Adaptation Wheel 4.49.1')){throw 'Outdated third-party notice'}
        if(!(Read-Entry $archive 'META-INF/mods.toml').Contains('MIT')){throw 'mods.toml licence is not MIT'}
        foreach($look in @('clockwork','ice_crown','armillary','event_horizon','ouroboros','storm_ring','malevolent_shrine','infinite_void')){Read-Entry $archive ('wheel_skins/'+$look+'.json') | Out-Null}
        if((Read-Entry $archive 'wheel_skins/event_horizon.json') -notmatch '"id": "m10-r2"'){throw 'Event Horizon is not the approved m10-r2 model'}
        if((Read-Entry $archive 'wheel_skins/armillary.json') -notmatch '"id": "m16"'){throw 'Armillary Sphere is not the approved m16 model'}
        # 4.38 Malevolent Shrine with its flash keys and the cuts around drawn by the client; 4.41 the approved m17-r3: blades in waves.
        # 4.39 Infinite Void: the approved m18; 4.40 m18-r2 with the red inner stream and the text adaptation.
        if((Read-Entry $archive 'wheel_skins/infinite_void.json') -notmatch '"id": "m18-r2"'){throw 'Infinite Void is not the approved m18-r2 model'}
        if(!$archive.GetEntry('ru/pasha/adaptationwheel/server/VoidTextAdaptation.class')){throw 'Void text adaptation missing'}
        $shrine=Read-Entry $archive 'wheel_skins/malevolent_shrine.json'
        if($shrine -notmatch '"id": "m17-r3"' -or $shrine -notmatch '"flashPeriodSec"' -or $shrine -notmatch '"flashFringeMat"'){throw 'Malevolent Shrine is not the approved m17-r3 model'}
        foreach($class in @('ShrineCuts','CutRibbon')){if(!$archive.GetEntry('ru/pasha/adaptationwheel/client/'+$class+'.class')){throw 'Shrine cuts missing'}}
        # 4.34 interface: category icons and the new texts (HUD title, list groups, tooltips) in both languages.
        if(!$archive.GetEntry('ru/pasha/adaptationwheel/client/CategoryIcons.class')){throw 'Category icons missing'}
        foreach($lang in @('ru_ru','en_us')){if((Read-Entry $archive ('assets/adaptationwheel/lang/'+$lang+'.json')) -notmatch '"gui.adaptationwheel.hud.title"'){throw "Interface texts missing: $lang"}}
        # 4.36 learning screen (variant C): the tile layout and its texts (tiles, study panel, number tiles) in both languages.
        if(!$archive.GetEntry('ru/pasha/adaptationwheel/client/LearningLayout.class')){throw 'Learning layout missing'}
        foreach($lang in @('ru_ru','en_us')){$text=Read-Entry $archive ('assets/adaptationwheel/lang/'+$lang+'.json');foreach($key in @('learning.adaptationwheel.lead','learning.adaptationwheel.mode.research.tag','learning.adaptationwheel.change.kept','learning.adaptationwheel.mode.research.knows','learning.adaptationwheel.study.left','learning.adaptationwheel.tile.observations.many','meta.adaptationwheel.changelog.4.36.0','meta.adaptationwheel.changelog.4.37.0','meta.adaptationwheel.changelog.4.39.0','meta.adaptationwheel.changelog.4.40.0','meta.adaptationwheel.changelog.4.43.0','meta.adaptationwheel.changelog.4.44.0','meta.adaptationwheel.changelog.4.45.1','meta.adaptationwheel.changelog.4.45.2','meta.adaptationwheel.changelog.4.46.0','meta.adaptationwheel.changelog.4.47.0','meta.adaptationwheel.changelog.4.48.0','meta.adaptationwheel.changelog.4.48.1','meta.adaptationwheel.changelog.4.49.0','meta.adaptationwheel.changelog.4.49.1','gui.adaptationwheel.card.speed_chip_full','gui.adaptationwheel.hud.hit_speed','learning.adaptationwheel.study.what','learning.adaptationwheel.study.what.pace','config.adaptationwheel.void_stream','config.adaptationwheel.void_stars','gui.adaptationwheel.origin.label','gui.adaptationwheel.origin.mod_without','gui.adaptationwheel.category.picker','skin.adaptationwheel.void.adapt','skin.adaptationwheel.void.read.short','skin.adaptationwheel.malevolent_shrine.description','config.adaptationwheel.shrine_cuts','gui.adaptationwheel.summary.title','gui.adaptationwheel.event.unworn','gui.adaptationwheel.summary.weak.unmet.few')){if(!$text.Contains('"'+$key+'"')){throw "Learning texts missing: $lang $key"}}}
        $expectedPayloads=@{
            # Preserve the reviewed optimization payload (Distant Horizons is no longer bundled since 4.49.0).
            'META-INF/jarjar/adapt-boost-1.20.1-forge-1.7.0.jar'=(Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $releaseRoot 'embedded/adapt-boost-1.20.1-forge-1.7.0.jar')).Hash.ToLower()
        }
        foreach($payload in $expectedPayloads.GetEnumerator()){
            $entry=$archive.GetEntry($payload.Key);if(!$entry -or (Entry-Hash $entry) -ne $payload.Value){throw "Bundled payload changed: $($payload.Key)"}
            $stream=$entry.Open();$nested=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Read)
            try{
                $required=@('pashaboost.mixins.json','LICENSE','META-INF/licenses/ModernFix-5.27.89-LGPL-3.0.txt','META-INF/licenses/ModernFix-GPL-3.0.txt','META-INF/licenses/MODERNFIX-SOURCES.md','META-INF/licenses/FerriteCore-6.0.1-MIT.txt')
                foreach($name in $required){if(!$nested.GetEntry($name)){throw "Missing bundled notice: $name"}}
            }finally{$nested.Dispose();$stream.Dispose()}
        }
        $classes=@{}
        foreach($entry in $archive.Entries){if($entry.FullName.EndsWith('.class')){$classes[$entry.FullName]=Entry-Hash $entry}}
        if($null -eq $baselineClasses){$baselineClasses=$classes}else{
            if($classes.Count -ne $baselineClasses.Count){throw 'Variant runtime classes differ'}
            foreach($key in $classes.Keys){if($classes[$key] -ne $baselineClasses[$key]){throw "Variant class unexpectedly differs: $key"}}
        }
    }finally{$archive.Dispose()}
    $destination=Join-Path $releaseDirectory $fileName
    Copy-Item -LiteralPath $builtJar -Destination $destination -Force
    $outputs+=$destination
}
$sourcePath=Join-Path $releaseDirectory ($releaseName+'-sources.zip')
$stream=[IO.File]::Open($sourcePath,[IO.FileMode]::Create)
$zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create)
try{
    $files=@()
    $files+=@(& git -c core.quotepath=off -C $releaseRoot ls-files | ForEach-Object {Get-Item -LiteralPath (Join-Path $releaseRoot $_)})
    foreach($name in @('embedded/adapt-boost-1.20.1-forge-1.7.0.jar','embedded-src/adapt-boost-1.20.1-forge-1.7.0-sources.zip')){$files+=Get-Item -LiteralPath (Join-Path $releaseRoot $name)}
    foreach($file in ($files | Sort-Object FullName -Unique)){
        $name=$file.FullName.Substring($releaseRoot.Length+1).Replace('\','/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip,$file.FullName,$name,[IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
}finally{$zip.Dispose();$stream.Dispose()}
$outputs+=$sourcePath
$hashes=Get-FileHash -Algorithm SHA256 -LiteralPath $outputs | ForEach-Object {$_.Hash.ToLower()+'  '+[IO.Path]::GetFileName($_.Path)}
[IO.File]::WriteAllLines((Join-Path $releaseDirectory ($releaseName+'-SHA256SUMS.txt')),$hashes,[Text.UTF8Encoding]::new($false))
Get-Item -LiteralPath $outputs | Select-Object FullName,Length
