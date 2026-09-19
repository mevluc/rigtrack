"""Maintain paired Android resources; missing translations are a build-review error."""
from pathlib import Path
from xml.sax.saxutils import escape

DATA = '''app_name|RigTrack|RigTrack
back|Back|Geri
home|Workspace|Çalışma alanı
home_intro|Camera motion, ready for post.|Kamera hareketi, post prodüksiyona hazır.
home_detail|Mount your phone securely. Set your reference. Capture every move.|Telefonu sağlamca sabitleyin. Referansı belirleyin. Hareketi kaydedin.
new_shot|New shot|Yeni çekim
recordings|Recordings|Kayıtlar
rig_profiles|Rig profiles|Rig profilleri
marker_maps|Marker maps|Marker haritaları
calibration|Camera calibration|Kamera kalibrasyonu
generator|Marker generator|Marker üretici
guide|Guide|Kullanım kılavuzu
settings|Settings|Ayarlar
diagnostics|Diagnostics|Tanılama
new_shot_hint|Configure the film camera and rig|Film kamerasını ve rigi ayarlayın
recordings_hint|Review and export your takes|Çekimleri inceleyin ve dışa aktarın
rig_hint|Phone to film camera alignment|Telefon ile film kamerasının hizası
maps_hint|Fixed references for the set|Set için sabit referanslar
calibration_hint|Improve marker pose accuracy|Marker poz doğruluğunu iyileştirin
generator_hint|Print markers at exact physical size|Markerları tam fiziksel boyutta yazdırın
guide_hint|From mounting to Blender|Montajdan Blender aktarımına
settings_hint|Language and capture preferences|Dil ve çekim tercihleri
diagnostics_hint|Device and pipeline information|Cihaz ve işlem hattı bilgileri
save|Save|Kaydet
cancel|Cancel|Vazgeç
close|Close|Kapat
start|Start|Başlat
continue_action|Continue|Devam
skip|Skip|Atla
done|Done|Tamam
delete|Delete|Sil
rename|Rename|Yeniden adlandır
duplicate|Use these settings|Bu ayarları kullan
export|Export .vfxtrack|.vfxtrack dışa aktar
export_csv|Export Blender CSV|Blender CSV dışa aktar
details|Details|Ayrıntılar
advanced|Advanced|Gelişmiş
name|Name|Ad
shot_name|Shot name|Çekim adı
camera|Film camera|Film kamerası
camera_preset|Camera preset|Kamera ön ayarı
search|Search manufacturer or model|Üretici veya model ara
custom_camera|Custom camera|Özel kamera
capture_mode|Capture mode|Çekim modu
fps|Film FPS|Film FPS
focal|Focal length (mm)|Odak uzaklığı (mm)
sensor_width|Active sensor width (mm)|Aktif sensör genişliği (mm)
sensor_height|Active sensor height (mm)|Aktif sensör yüksekliği (mm)
resolution_width|Image width (px)|Görüntü genişliği (px)
resolution_height|Image height (px)|Görüntü yüksekliği (px)
focus|Focus distance (m; 0 = unknown)|Netleme mesafesi (m; 0 = bilinmiyor)
lens|Lens name|Lens adı
no_map|No map|Harita yok
smoothing|Offline smoothing|Çevrimdışı yumuşatma
off|Off|Kapalı
low|Low|Düşük
medium|Medium|Orta
high|High|Yüksek
marker_rate|Marker detection FPS|Marker algılama FPS
ar_fps|Preferred AR FPS|Tercih edilen AR FPS
auto|Auto|Otomatik
developer|Developer mode|Geliştirici modu
synthetic|Synthetic test|Sentetik test
synthetic_hint|Simulated motion. No physical camera tracking.|Simüle hareket. Fiziksel kamera takibi yapılmaz.
motion|Test motion|Test hareketi
capture|Capture|Çekim
origin|Set origin|Orijin belirle
origin_set|Origin set|Orijin belirlendi
origin_missing|Set origin before recording|Kayıttan önce orijini belirleyin
record|Record|Kaydet
stop|Stop|Durdur
sync|Sync|Senkron
markers|Markers|Markerlar
axes|Axes|Eksenler
tools|Tools|Araçlar
relocalize|Relocalize map|Haritayı yeniden hizala
board_reference|Board reference|Pano referansı
excellent|Excellent|Mükemmel
good|Good|İyi
fair|Fair|Orta
poor|Poor|Zayıf
lost|Lost|Kayıp
quality|Tracking quality|Takip kalitesi
quality_tip|Use textured surfaces, steady motion and visible fixed markers.|Dokulu yüzeyler, yumuşak hareket ve görünür sabit markerlar kullanın.
quality_good|Reference is stable. Keep markers visible during the move.|Referans kararlı. Hareket sırasında markerları görünür tutun.
tracking|Tracking|Takip ediliyor
initializing|Initializing|Başlatılıyor
duration|Duration|Süre
world_lock|Marker world lock|Marker dünya kilidi
enabled|Enabled|Etkin
disabled|Disabled|Devre dışı
free_storage|Free storage|Boş depolama
thermal|Thermal status|Isıl durum
gyro|Gyroscope|Jiroskop
accel|Accelerometer|İvmeölçer
ar_rate|AR camera rate|AR kamera hızı
detector_rate|Detector rate|Algılayıcı hızı
dropped|Dropped marker frames|Atlanan marker kareleri
mapped|Mapped markers|Haritalanmış markerlar
technical_details|Technical details|Teknik ayrıntılar
record_exit_title|Save and leave capture?|Kaydedip çekimden çıkılsın mı?
record_exit_body|Recording is active. Stop and save this take before leaving.|Kayıt sürüyor. Çıkmadan önce çekimi durdurup kaydedin.
stop_save|Stop, save and leave|Durdur, kaydet ve çık
double_back|Press back again to exit|Çıkmak için tekrar geri tuşuna basın
delete_title|Delete recording?|Kayıt silinsin mi?
delete_body|This permanently deletes the local recording and its raw data.|Bu işlem yerel kaydı ve ham verilerini kalıcı olarak siler.
empty_recordings|No recordings yet|Henüz kayıt yok
empty_recordings_hint|Start a new shot. Completed takes appear here.|Yeni bir çekim başlatın. Tamamlanan çekimler burada görünür.
empty_maps|No marker maps|Marker haritası yok
empty_maps_hint|Create a map from fixed markers before your first take.|İlk çekimden önce sabit markerlardan bir harita oluşturun.
summary|Shot summary|Çekim özeti
exported|Exported|Dışa aktarıldı
not_exported|Not exported|Dışa aktarılmadı
recover|Recover unfinished recording|Yarım kalan kaydı kurtar
unfinished|Recording was interrupted. Recover complete rows before export.|Kayıt kesildi. Dışa aktarmadan önce tamamlanmış satırları kurtarın.
export_complete|Export complete|Dışa aktarma tamamlandı
saved|Saved|Kaydedildi
error_title|Action could not be completed|İşlem tamamlanamadı
error_generic|Check your settings and try again. Technical details are available in Diagnostics.|Ayarları kontrol edip yeniden deneyin. Teknik ayrıntılar Tanılama ekranında bulunur.
invalid_value|Enter a valid value within the indicated limits.|Belirtilen sınırlar içinde geçerli bir değer girin.
origin_required|Wait for tracking, then set the origin.|Takibin başlamasını bekleyip orijini belirleyin.
mapped_required|Show a fixed marker from the selected map, then try again.|Seçili haritadaki sabit bir markerı gösterip yeniden deneyin.
stop_first|Stop recording before changing the reference.|Referansı değiştirmeden önce kaydı durdurun.
record_first|Start recording before adding a sync cue.|Senkron işaretinden önce kaydı başlatın.
permission_title|Camera access|Kamera erişimi
permission_body|RigTrack uses the camera for ARCore motion tracking and marker detection. Frames stay on your device.|RigTrack kamerayı ARCore hareket takibi ve marker algılama için kullanır. Kareler cihazınızda kalır.
permission_denied|Allow camera access in Android settings to use physical tracking.|Fiziksel takip için Android ayarlarından kamera erişimine izin verin.
open_settings|Open Android settings|Android ayarlarını aç
unsupported|Physical tracking is unavailable on this device. Check ARCore support and installation.|Bu cihazda fiziksel takip kullanılamıyor. ARCore desteğini ve kurulumunu kontrol edin.
storage_low|Storage is low. Export and remove older takes before a long recording.|Depolama azaldı. Uzun kayıttan önce eski çekimleri dışa aktarıp silin.
storage_critical|Recording stopped safely because storage is critically low.|Depolama kritik düzeyde azaldığı için kayıt güvenle durduruldu.
general|General|Genel
language|Language|Dil
keep_awake|Keep screen awake|Ekranı açık tut
recording_settings|Recording|Kayıt
sound|Sync sound|Senkron sesi
haptic|Sync vibration|Senkron titreşimi
interface_settings|Interface|Arayüz
about|About|Hakkında
offline_notice|Offline. No accounts, analytics or cloud uploads.|Çevrimdışı. Hesap, analiz takibi veya buluta yükleme yok.
sync_notice|Sync stores cue request time. Physical sound, light and vibration latency is not measured.|Senkron, işaretin istenme anını saklar. Gerçek ses, ışık ve titreşim gecikmesi ölçülmez.
reopen_onboarding|Show introduction again|Tanıtımı yeniden göster
dictionary|Marker dictionary|Marker sözlüğü
marker_size|Black-square size (mm)|Siyah kare boyutu (mm)
min_area|Minimum marker area (px²)|Minimum marker alanı (px²)
max_error|Maximum reprojection error (px)|Maksimum yeniden projeksiyon hatası (px)
correction|Correction strength (0–1)|Düzeltme gücü (0–1)
confidence|Minimum confidence (0–1)|Minimum güven (0–1)
gap|Maximum interpolation gap (ms)|Maksimum ara değer boşluğu (ms)
sensor_period|Sensor period (µs; 0 = fastest)|Sensör aralığı (µs; 0 = en hızlı)
logging|Detailed diagnostic logging|Ayrıntılı tanılama günlüğü
add_profile|Add rig profile|Rig profili ekle
horizontal|Horizontal offset (mm; right +)|Yatay ofset (mm; sağ +)
vertical|Vertical offset (mm; up +)|Dikey ofset (mm; yukarı +)
forward|Forward offset (mm; forward +)|İleri ofset (mm; ileri +)
yaw|Yaw (degrees)|Yatay dönüş (derece)
pitch|Pitch (degrees)|Dikey dönüş (derece)
roll|Roll (degrees)|Yatış (derece)
rig_explanation|Measure from the phone optical center to the film camera optical center. Keep the mount rigid.|Telefon optik merkezinden film kamerası optik merkezine ölçün. Bağlantıyı rijit tutun.
rig_axes|Phone camera axes: +X right, +Y up, −Z forward. Rotation order: Rz × Ry × Rx.|Telefon kamera eksenleri: +X sağ, +Y yukarı, −Z ileri. Dönüş sırası: Rz × Ry × Rx.
new_map|New marker map|Yeni marker haritası
map_name|Map name|Harita adı
map_sizes|ID:size overrides (e.g. 1:150, 12:200)|ID:boyut değerleri (ör. 1:150, 12:200)
map_instruction|Keep markers fixed. Set origin, collect varied views, then save. Relocalize saved maps in each new AR session.|Markerları sabit tutun. Orijin belirleyin, farklı açılardan örnek toplayıp kaydedin. Kayıtlı haritayı her yeni AR oturumunda yeniden hizalayın.
collect|Collect samples|Örnek topla
save_map|Save map|Haritayı kaydet
samples|Samples|Örnekler
solve_calibration|Solve and save calibration|Kalibrasyonu hesapla ve kaydet
calibration_instruction|Use the printed board below. Vary distance, tilt and frame position. Collect at least 15 diverse views; 25 recommended.|Aşağıdaki ayarlara uygun panoyu kullanın. Mesafeyi, eğimi ve kadrajdaki konumu değiştirin. En az 15 farklı görünüm toplayın; 25 önerilir.
calibration_apply|Calibration saved. Reopen capture to apply it.|Kalibrasyon kaydedildi. Uygulamak için çekimi yeniden açın.
cal_more|Show at least six board corners.|Panonun en az altı köşesini gösterin.
cal_closer|Move the board closer.|Panoyu yaklaştırın.
cal_angle|Change the board angle and position.|Panonun açısını ve konumunu değiştirin.
cal_ready|Enough samples to solve; more varied views can improve accuracy.|Hesaplama için yeterli örnek var; farklı açılar doğruluğu artırabilir.
cal_good|Good sample accepted.|İyi örnek kabul edildi.
cal_restart|Resolution changed. Restart calibration.|Çözünürlük değişti. Kalibrasyonu yeniden başlatın.
cal_bad|Calibration could not be solved accurately. Collect sharper, more varied views.|Kalibrasyon yeterli doğrulukta hesaplanamadı. Daha net ve farklı açılardan örnekler toplayın.
marker_tab|Markers|Markerlar
charuco_tab|ChArUco board|ChArUco panosu
first_id|First ID|İlk ID
last_id|Last ID|Son ID
paper|Paper|Kâğıt
orientation|Orientation|Yön
portrait|Portrait|Dikey
landscape|Landscape|Yatay
multi_page|Multiple markers per page|Sayfa başına birden çok marker
labels|Print labels|Etiketleri yazdır
ruler|100 mm check ruler|100 mm kontrol cetveli
preview|Preview|Önizleme
save_pdf|Save PDF|PDF kaydet
print_warning|Print at 100% / Actual size. Disable Fit to page. Measure the black square before use.|%100 / Gerçek boyutta yazdırın. Sayfaya sığdır seçeneğini kapatın. Kullanmadan önce siyah kareyi ölçün.
paper_fit|The selected pattern does not fit this paper with safe margins. Choose larger paper or a smaller pattern.|Seçili desen güvenli kenar boşluklarıyla kâğıda sığmıyor. Daha büyük kâğıt veya daha küçük desen seçin.
board_columns|Board columns|Pano sütunları
board_rows|Board rows|Pano satırları
square_size|Square size (mm)|Kare boyutu (mm)
board_marker_size|Inner marker size (mm)|İç marker boyutu (mm)
board_match|These settings must match the printed calibration board.|Bu ayarlar basılı kalibrasyon panosuyla aynı olmalıdır.
verified|Manufacturer source verified|Üretici kaynağı doğrulandı
unverified_area|Active recording area is not verified. Enter the active dimensions for your recording mode.|Aktif kayıt alanı doğrulanmadı. Kayıt modunuzun aktif boyutlarını girin.
source|Source|Kaynak
sensor|Sensor|Sensör
onboard_1|Mount securely|Sağlam sabitleyin
onboard_1_body|Attach the phone rigidly to your film camera. Measure the optical-center offset in Rig profiles.|Telefonu film kamerasına rijit şekilde bağlayın. Optik merkez ofsetini Rig profillerine girin.
onboard_2|Establish a reference|Referans oluşturun
onboard_2_body|Use correctly sized, fixed markers. Calibrate the phone camera, map the set and set the origin.|Doğru boyutta, sabit markerlar kullanın. Telefon kamerasını kalibre edin, seti haritalayın ve orijini belirleyin.
onboard_3|Record and synchronize|Kaydedin ve senkronlayın
onboard_3_body|Choose the film FPS and active sensor area. Record and add sync cues that your film camera can hear.|Film FPS değerini ve aktif sensör alanını seçin. Kaydı başlatıp film kamerasının duyabileceği senkron işaretleri ekleyin.
onboard_4|Bring motion into Blender|Hareketi Blender aktarın
onboard_4_body|Export the .vfxtrack file. Use the supplied Blender importer and align a sync cue to your footage.|.vfxtrack dosyasını dışa aktarın. Sağlanan Blender içe aktarıcısıyla açıp senkron işaretini görüntüyle hizalayın.'''

DATA += '''
no_offset|No offset|Ofset yok
motion_circle|Circle|Daire
motion_forward|Forward / back|İleri / geri
motion_pan|Pan|Yatay dönüş
motion_orbit|Orbit|Yörünge
motion_test|Test trajectory|Test yörüngesi
stats|Statistics|İstatistikler
area_confirm|I verified these active sensor dimensions for this recording mode|Bu kayıt modunun aktif sensör boyutlarını doğruladım
thermal_none|Normal|Normal
thermal_light|Light|Hafif
thermal_moderate|Moderate|Orta
thermal_severe|Severe|Yüksek
thermal_critical|Critical|Kritik
thermal_emergency|Emergency|Acil
thermal_shutdown|Shutdown|Kapanma
unavailable|Unavailable|Kullanılamıyor
film_frames|Film frames|Film kareleri
invalid_frames|Invalid frames|Geçersiz kareler
lost_duration|Tracking lost duration|Takip kayıp süresi
world_corrections|World-reference corrections|Dünya referansı düzeltmeleri
mean_error|Mean reprojection error|Ortalama yeniden projeksiyon hatası
page_number|Page|Sayfa
previous|Previous|Önceki
next|Next|Sonraki
translation_dead_zone|Translation dead zone|Konum ölü bölgesi
rotation_dead_zone|Rotation dead zone|Dönüş ölü bölgesi
relock_confirmation|Re-lock confirmation frames|Yeniden kilit onay karesi
translation_blend|Translation blend|Konum geçişi
rotation_blend|Rotation blend|Dönüş geçişi
minimum_confidence|Minimum marker confidence|En düşük marker güveni
gap_repair_limit|Offline gap repair limit|Çevrimdışı boşluk onarım sınırı
tracking_refinement|Tracking refinement|Takip iyileştirme
raw_jitter|RAW jitter|RAW titreşim
fused_jitter|FUSED jitter|FUSED titreşim
refined_jitter|REFINED jitter|REFINED titreşim
rejected_outliers|Rejected outliers|Reddedilen aykırı örnekler
repaired_samples|Repaired samples|Onarılan örnekler
relocks|Marker re-locks|Marker yeniden kilitleri
moved_markers|Moved markers|Taşınmış markerlar
average_processing|Average marker processing|Ortalama marker işleme
track_quality|Track quality|Takip kalitesi
rejected_marker_observations|Rejected marker observations|Reddedilen marker gözlemleri
repaired_gaps|Repaired gaps|Onarılan boşluklar
longest_repaired_gap|Longest repaired gap|En uzun onarılan boşluk
unrepaired_gaps|Unrepaired long gaps|Onarılmayan uzun boşluklar
sample_counts|RAW / FUSED / REFINED samples|RAW / FUSED / REFINED örnekleri
maximum_deviation|Maximum refined deviation|En büyük refined sapması
marker_used|Used|Kullanıldı
marker_valid|Valid|Geçerli
marker_mapped|Mapped|Haritalanmış
awaiting_confirmation|Awaiting confirmation|Onay bekleniyor
reject_low_confidence|Low confidence|Düşük güven
reject_reprojection|High reprojection error|Yüksek projeksiyon hatası
reject_translation|Translation outlier|Konum aykırı değeri
reject_rotation|Rotation outlier|Dönüş aykırı değeri
reject_moved|Marker may have moved|Marker taşınmış olabilir
reject_map_quality|Low map quality|Düşük harita kalitesi
reject_tracking|ARCore not tracking|ARCore takip etmiyor
reject_not_mapped|Not mapped|Haritalanmamış'''

GUIDE = [
('quick', 'Quick start', 'Hızlı başlangıç',
 '1. Secure the phone to the camera. 2. Enter the measured rig offset and film camera settings. 3. Calibrate the phone lens with ChArUco. 4. Place fixed markers and create a map. 5. Open a shot, wait for tracking, relocalize a saved map or set a fresh origin. 6. Record and add sync cues. 7. Stop, export and import into Blender.',
 '1. Telefonu kameraya sabitleyin. 2. Ölçtüğünüz rig ofsetini ve film kamera ayarlarını girin. 3. Telefon lensini ChArUco ile kalibre edin. 4. Sabit markerlar yerleştirip harita oluşturun. 5. Çekimi açın, takibi bekleyin, kayıtlı haritayı yeniden hizalayın veya yeni orijin belirleyin. 6. Kaydı başlatıp senkron işaretleri ekleyin. 7. Durdurun, dışa aktarın ve Blender içine alın.'),
('how', 'How tracking works', 'Takip nasıl çalışır?',
 'ARCore estimates physical phone-camera motion from images and inertial sensors. RigTrack records that pose relative to an anchor. Fixed mapped markers provide an additional world reference. The measured rigid offset converts the phone pose into the film-camera pose. Raw IMU samples remain a separate diagnostic stream; they are not a second independent position solution.',
 'ARCore, görüntülerden ve atalet sensörlerinden fiziksel telefon kamerasının hareketini kestirir. RigTrack bu pozu bir anchor referansına göre kaydeder. Haritalanmış sabit markerlar ek dünya referansı sağlar. Ölçülmüş rijit ofset, telefon pozunu film kamerası pozuna dönüştürür. Ham IMU örnekleri ayrı tanılama verisidir; bağımsız ikinci bir konum çözümü değildir.'),
('mount', 'Phone mounting', 'Telefon montajı',
 'Use a rigid clamp and secure every joint. The phone camera must see the environment throughout the move. Avoid flexible arms, loose cases and touching the phone during the take. A change in phone-to-camera alignment invalidates the rig profile. Recheck alignment after lens or accessory changes. Test a short move before the production take.',
 'Rijit bir kelepçe kullanıp tüm bağlantıları sıkın. Telefon kamerası hareket boyunca çevreyi görmelidir. Esnek kollardan, gevşek kılıflardan ve çekim sırasında telefona dokunmaktan kaçının. Telefon ile kamera hizasının değişmesi rig profilini geçersiz kılar. Lens veya aksesuar değişiminden sonra hizayı kontrol edin. Asıl çekimden önce kısa bir deneme yapın.'),
('rig', 'Rig offset and rotation', 'Rig ofseti ve dönüşü',
 'Measure from the phone optical center to the film-camera optical center, in millimetres. Positive horizontal is right, positive vertical is up and the editor defines positive forward toward the scene. Internally camera forward is −Z. Enter pitch, yaw and roll only when the cameras are not parallel. Rotation composition is Rz × Ry × Rx. Incorrect offsets become especially visible during rotation.',
 'Telefon optik merkezinden film kamerasının optik merkezine milimetreyle ölçün. Pozitif yatay sağa, pozitif dikey yukarıya; editörde pozitif ileri sahneye doğrudur. İç koordinatlarda kamera ilerisi −Z yönüdür. Kameralar paralel değilse dikey dönüş, yatay dönüş ve yatış girin. Dönüş bileşimi Rz × Ry × Rx şeklindedir. Yanlış ofsetler özellikle dönüşte görünür hale gelir.'),
('camera', 'Camera presets and field of view', 'Kamera ön ayarları ve görüş açısı',
 'Select the exact recording mode, not just the camera model. Active sensor dimensions can differ from the physical sensor because of cropping. Output pixel dimensions alone do not prove active sensor size. Use verified mode dimensions or enter the manufacturer-specified active area. Enter the focal length used for the take separately. Focus breathing and lens distortion can still affect the match.',
 'Yalnızca kamera modelini değil, tam kayıt modunu seçin. Kırpma nedeniyle aktif sensör boyutları fiziksel sensörden farklı olabilir. Çıktı piksel boyutları tek başına aktif sensör boyutunu kanıtlamaz. Doğrulanmış mod boyutlarını kullanın veya üreticinin belirttiği aktif alanı girin. Çekimde kullanılan odak uzaklığını ayrıca girin. Netleme soluması ve lens distorsiyonu eşleşmeyi etkileyebilir.'),
('cal', 'Phone-camera calibration', 'Telefon kamerası kalibrasyonu',
 'Generate a ChArUco board with the same dictionary, rows, columns, square size and inner-marker size as the calibration settings. Print at actual size and keep it flat. Show at least six corners. Vary distance, tilt and frame position; repeated identical views do not add useful samples. At least 15 diverse views are required and 25 are recommended. Solve and save, then reopen capture. The profile is tied to the device, camera and AR configuration.',
 'Kalibrasyon ayarlarıyla aynı sözlük, satır, sütun, kare ve iç marker boyutunda ChArUco panosu üretin. Gerçek boyutta basıp düz tutun. En az altı köşeyi gösterin. Mesafeyi, eğimi ve kadrajdaki konumu değiştirin; aynı görünümü tekrarlamak yararlı örnek eklemez. En az 15 farklı görünüm gerekir, 25 önerilir. Hesaplayıp kaydedin ve çekimi yeniden açın. Profil cihaz, kamera ve AR yapılandırmasına bağlıdır.'),
('print', 'Printing markers', 'Marker yazdırma',
 'The marker size is the outside edge of the black square, not the paper or white margin. Use 100% / Actual size and disable Fit to page. Measure the square and optional 100 mm ruler with a real ruler. Choose larger paper if the requested size does not fit; the generator must never shrink it. Keep white space around the marker, avoid glossy reflections and attach prints to a flat surface.',
 'Marker boyutu kâğıdın veya beyaz boşluğun değil, siyah karenin dış kenarıdır. %100 / Gerçek boyut kullanıp Sayfaya sığdır seçeneğini kapatın. Kareyi ve isteğe bağlı 100 mm cetveli gerçek cetvelle ölçün. İstenen boyut sığmıyorsa daha büyük kâğıt seçin; üretici deseni küçültmemelidir. Çevrede beyaz boşluk bırakın, parlak yansımalardan kaçının ve baskıyı düz yüzeye yapıştırın.'),
('placement', 'Set placement', 'Set yerleşimi',
 'Use unique IDs and keep every mapped marker fixed. Spread markers through the move at different viewing directions and heights. Avoid tiny distant markers, extreme viewing angles, motion blur and occlusion. Larger printed markers are easier to observe at distance. A moved marker no longer matches its saved world pose: remove it from the map or recalibrate the map.',
 'Benzersiz ID kullanın ve haritadaki her markerı sabit tutun. Markerları hareket boyunca farklı bakış yönlerine ve yüksekliklere dağıtın. Küçük ve uzak markerlardan, aşırı açılardan, hareket bulanıklığından ve kapanmalardan kaçının. Büyük baskılar uzaktan daha kolay gözlenir. Taşınmış marker kayıtlı dünya pozuyla eşleşmez; haritadan çıkarın veya haritayı yeniden kalibre edin.'),
('mapping', 'Marker map calibration', 'Marker haritası kalibrasyonu',
 'Choose the dictionary and real black-square sizes. Use ID:size overrides for mixed-size markers. Set the origin, begin collecting and move slowly through varied views. Each reference needs enough accepted observations, generally 30–100. Save the map only while the set remains unchanged. An existing map must be relocalized before adding observations in a new session.',
 'Sözlüğü ve gerçek siyah kare boyutlarını seçin. Farklı boyutlu markerlar için ID:boyut değerlerini kullanın. Orijin belirleyip toplamayı başlatın ve farklı açılardan yavaşça hareket edin. Her referans için yeterli kabul edilmiş gözlem, genellikle 30–100 örnek gerekir. Haritayı set değişmeden kaydedin. Mevcut haritaya yeni oturumda gözlem eklemeden önce yeniden hizalama gerekir.'),
('relocalization', 'Relocalization', 'Yeniden hizalama',
 'A new AR session has a new coordinate system. Loading a marker map does not align that coordinate system automatically. Show a good fixed marker from the map and select Relocalize map before recording. The reference must be recent and confidently detected. A fresh Set origin creates a new origin and does not recover an old map alignment.',
 'Yeni AR oturumunun koordinat sistemi yenidir. Marker haritasını yüklemek bu sistemi otomatik hizalamaz. Kayıttan önce haritadaki sabit bir markerı net biçimde gösterip Haritayı yeniden hizala seçeneğini kullanın. Referans güncel ve güvenle algılanmış olmalıdır. Yeni Orijin belirle işlemi yeni başlangıç oluşturur; eski harita hizasını geri getirmez.'),
('origin_guide', 'Setting the origin', 'Orijin belirleme',
 'Wait for stable tracking, hold the rig at the intended reference pose and select Set origin. The recorded pose is anchor-relative. Do not change origin during a recording. If a saved map is required, use Relocalize map instead. Plan where the origin belongs in the Blender scene and document the reference pose for the team.',
 'Kararlı takibi bekleyin, rigi amaçlanan referans pozunda tutup Orijin belirle seçeneğini kullanın. Kaydedilen poz anchor referansına göredir. Kayıt sırasında orijini değiştirmeyin. Kayıtlı harita gerekiyorsa Haritayı yeniden hizala seçeneğini kullanın. Orijinin Blender sahnesindeki yerini planlayıp referans pozunu ekiple paylaşın.'),
('record_guide', 'Recording a take', 'Çekim kaydetme',
 'Confirm the film FPS, focal length, active sensor area, rig and map. Check storage and tracking quality. Set or relocalize the origin, then press the red record control. Add sync cues near the beginning and end. Stop normally and wait for export processing. Leaving capture asks to stop and save. Backgrounding the app safely ends the active recording and marks it interrupted.',
 'Film FPS değerini, odak uzaklığını, aktif sensör alanını, rigi ve haritayı doğrulayın. Depolamayı ve takip kalitesini kontrol edin. Orijin belirleyin veya yeniden hizalayın, ardından kırmızı kayıt düğmesine basın. Başlangıç ve sona yakın senkron işaretleri ekleyin. Normal şekilde durdurup dışa aktarma işlemesini bekleyin. Çekimden çıkarken durdurup kaydetme sorulur. Uygulamayı arka plana almak kaydı güvenle bitirir ve kesintiyi işaretler.'),
('sync_guide', 'Sync cues', 'Senkron işaretleri',
 'SYNC records a monotonic cue-request timestamp and can emit sound, a screen flash and vibration. Physical onset is not hardware timestamped. Let the film camera record the cue sound, then align that cue in post. Multiple cues can help reveal clock drift. Do not assume phone time and camera timecode are already synchronized.',
 'SENKRON monoton bir işaret-istek zaman damgası kaydeder; ses, ekran flaşı ve titreşim üretebilir. Fiziksel başlangıç donanımsal olarak zaman damgalanmaz. Film kamerasına işaret sesini kaydettirin, ardından post aşamasında bu işareti hizalayın. Birden çok işaret saat kaymasını görmeye yardımcı olabilir. Telefon saatiyle kamera zaman kodunun zaten senkron olduğunu varsaymayın.'),
('quality_guide', 'Quality and tracking loss', 'Kalite ve takip kaybı',
 'Quality combines tracking validity, cadence and mapped-marker confidence. It is a useful indicator, not a measured millimetre-accuracy guarantee. Add texture and light, reduce blur and keep references visible. Long gaps are exported as invalid frames rather than invented camera motion. Review loss duration, invalid frames and reprojection errors in the summary before using the take.',
 'Kalite; takip geçerliliğini, örnekleme düzenini ve haritalanmış marker güvenini birleştirir. Yararlı bir göstergedir, ölçülmüş milimetre doğruluğu garantisi değildir. Doku ve ışığı artırın, bulanıklığı azaltıp referansları görünür tutun. Uzun boşluklar uydurulmuş kamera hareketi yerine geçersiz kareler olarak dışa aktarılır. Çekimi kullanmadan önce kayıp süresini, geçersiz kareleri ve yeniden projeksiyon hatalarını inceleyin.'),
('export_guide', 'Export and recovery', 'Dışa aktarma ve kurtarma',
 'A .vfxtrack container is a ZIP containing metadata, raw streams, derived tracks, diagnostics and reference snapshots. Keep it with the original film footage. Export through the Android document picker. CSV-only export is convenient for the fused Blender camera, but the full container preserves more evidence. If recording was interrupted, recover the complete rows from the retained partial recording before export.',
 '.vfxtrack; metadata, ham akışlar, türetilmiş takip, tanılama ve referans kopyalarını içeren ZIP dosyasıdır. Orijinal film görüntüsüyle birlikte saklayın. Android dosya seçicisiyle dışa aktarın. Yalnızca CSV aktarımı birleşik Blender kamerası için pratiktir; tam paket daha fazla veri korur. Kayıt kesildiyse dışa aktarmadan önce saklanan kısmi kayıttaki tamamlanmış satırları kurtarın.'),
('blender', 'Blender import', 'Blender içe aktarma',
 'Install tools/blender/import_vfxtrack.py as an add-on and import the container. Choose raw or fused motion, start frame, sync offset and scale. The importer converts ARCore coordinates to Blender and sets film FPS, including rational rates such as 23.976. Align a recorded cue to the film. Check focal length, sensor fit and a known physical move before rendering. Invalid tracking frames are marked for review.',
 'tools/blender/import_vfxtrack.py dosyasını eklenti olarak kurup paketi içe aktarın. Ham veya birleşik hareketi, başlangıç karesini, senkron ofsetini ve ölçeği seçin. İçe aktarıcı ARCore koordinatlarını Blender sistemine dönüştürür ve 23.976 gibi rasyonel hızlar dahil film FPS değerini ayarlar. Kayıtlı işareti filme hizalayın. Render öncesi odak uzaklığını, sensör uyumunu ve bilinen fiziksel hareketi kontrol edin. Geçersiz takip kareleri inceleme için işaretlenir.'),
('raw_fused', 'Raw and fused tracks', 'Ham ve birleşik takip',
 'Raw means the original ARCore pose after the documented coordinate and rig transforms. Fused adds the marker world-reference correction. Offline smoothing affects only derived output and never rewrites the original sensor streams. Compare raw and fused when markers disagree, move or become occluded. Preserve both tracks so the post team can choose the most reliable result.',
 'Ham takip, belgelenmiş koordinat ve rig dönüşümleri uygulanmış orijinal ARCore pozudur. Birleşik takip marker dünya referansı düzeltmesini ekler. Çevrimdışı yumuşatma yalnızca türetilmiş çıktıyı etkiler, orijinal sensör akışlarını değiştirmez. Markerlar uyuşmadığında, taşındığında veya kapandığında ham ve birleşik takibi karşılaştırın. Post ekibinin güvenilir sonucu seçebilmesi için ikisini de koruyun.'),
('troubleshoot', 'Troubleshooting', 'Sorun giderme',
 'No preview: check camera permission and ARCore support. No markers: verify dictionary, print size, focus and white border. Unstable correction: check fixed placement, map alignment and lens calibration. Wrong field of view: verify active sensor crop and focal length. Offset during rotation: remeasure the rig. Missing frames: inspect tracking loss, storage and thermal status. Keep the raw container and diagnostic details when investigating a failure.',
 'Önizleme yoksa kamera iznini ve ARCore desteğini kontrol edin. Marker görünmüyorsa sözlüğü, baskı boyutunu, netliği ve beyaz kenarı doğrulayın. Düzeltme kararsızsa sabit yerleşimi, harita hizasını ve lens kalibrasyonunu kontrol edin. Görüş açısı yanlışsa aktif sensör kırpmasını ve odak uzaklığını doğrulayın. Dönüşte ofset varsa rigi yeniden ölçün. Eksik karelerde takip kaybını, depolamayı ve ısıl durumu inceleyin. Hata araştırırken ham paketi ve tanılama ayrıntılarını saklayın.'),
]

def write():
    root = Path(__file__).resolve().parents[1] / 'app/src/main/res'
    rows = [line.split('|') for line in DATA.splitlines()]
    for key, en_title, tr_title, en_body, tr_body in GUIDE:
        rows += [[f'guide_{key}_title', en_title, tr_title], [f'guide_{key}_body', en_body, tr_body]]
    assert all(len(row) == 3 for row in rows)
    assert len({row[0] for row in rows}) == len(rows)
    for index, folder in [(1, 'values'), (2, 'values-tr')]:
        directory = root / folder
        directory.mkdir(parents=True, exist_ok=True)
        strings = ['<resources>']
        for row in rows:
            value = escape(row[index]).replace("'", "\\'")
            strings.append(f'    <string name="{row[0]}" formatted="false">{value}</string>')
        strings.append('</resources>')
        (directory / 'strings.xml').write_text('\n'.join(strings) + '\n', encoding='utf-8')

if __name__ == '__main__':
    write()
