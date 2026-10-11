#|
$JSON
{"authURL":["ai2.appinventor.mit.edu"],"YaVersion":"230","Source":"Form","Properties":{"$Name":"Screen1","$Type":"Form","$Version":"31","AlignHorizontal":"3","AppName":"RobotArmBLE","BackgroundColor":"&HFFF5F7FA","ScreenOrientation":"portrait","Scrollable":"True","Sizing":"Responsive","Theme":"AppTheme.Light.DarkActionBar","PrimaryColor":"&HFF1565C0","PrimaryColorDark":"&HFF0D47A1","AccentColor":"&HFFFF9800","Title":"แขนกล BLE","Uuid":"0","$Components":[
{"$Name":"LabelTitle","$Type":"Label","$Version":"5","FontBold":"True","FontSize":"22","HasMargins":"True","Text":"ควบคุมแขนกลผ่านบลูทูธ","TextAlignment":"1","TextColor":"&HFF0D47A1","Width":"-2","Uuid":"1001"},
{"$Name":"LabelStatus","$Type":"Label","$Version":"5","FontSize":"16","Text":"สถานะ: ยังไม่เชื่อมต่อ","TextAlignment":"1","TextColor":"&HFFC62828","Width":"-2","Uuid":"1002"},
{"$Name":"HorizontalConnect","$Type":"HorizontalArrangement","$Version":"4","AlignHorizontal":"3","Width":"-2","Uuid":"1003","$Components":[
{"$Name":"ButtonScan","$Type":"Button","$Version":"7","BackgroundColor":"&HFF0277BD","FontBold":"True","FontSize":"15","Shape":"1","Text":"1. ค้นหา","TextColor":"&HFFFFFFFF","Width":"-1032","Uuid":"1021"},
{"$Name":"ListPickerBT","$Type":"ListPicker","$Version":"9","BackgroundColor":"&HFF1565C0","FontBold":"True","FontSize":"15","Shape":"1","Text":"2. เลือกอุปกรณ์","TextColor":"&HFFFFFFFF","Title":"เลือกโมดูลบลูทูธ","Width":"-1032","Uuid":"1004"},
{"$Name":"ButtonDisconnect","$Type":"Button","$Version":"7","BackgroundColor":"&HFF9E9E9E","FontSize":"15","Shape":"1","Text":"ตัดการเชื่อมต่อ","TextColor":"&HFFFFFFFF","Width":"-1032","Uuid":"1005"}]},
{"$Name":"LabelBase","$Type":"Label","$Version":"5","FontBold":"True","FontSize":"18","HasMargins":"True","Text":"ฐานหมุน: 90°","Width":"-2","Uuid":"1006"},
{"$Name":"SliderBase","$Type":"Slider","$Version":"2","ColorLeft":"&HFF1565C0","MaxValue":"180.0","MinValue":"0.0","ThumbPosition":"90.0","Width":"-2","Uuid":"1007"},
{"$Name":"LabelShoulder","$Type":"Label","$Version":"5","FontBold":"True","FontSize":"18","HasMargins":"True","Text":"ไหล่: 90°","Width":"-2","Uuid":"1008"},
{"$Name":"SliderShoulder","$Type":"Slider","$Version":"2","ColorLeft":"&HFF1565C0","MaxValue":"180.0","MinValue":"0.0","ThumbPosition":"90.0","Width":"-2","Uuid":"1009"},
{"$Name":"LabelElbow","$Type":"Label","$Version":"5","FontBold":"True","FontSize":"18","HasMargins":"True","Text":"ข้อศอก: 90°","Width":"-2","Uuid":"1010"},
{"$Name":"SliderElbow","$Type":"Slider","$Version":"2","ColorLeft":"&HFF1565C0","MaxValue":"180.0","MinValue":"0.0","ThumbPosition":"90.0","Width":"-2","Uuid":"1011"},
{"$Name":"LabelGripper","$Type":"Label","$Version":"5","FontBold":"True","FontSize":"18","HasMargins":"True","Text":"มือจับ: 90°","Width":"-2","Uuid":"1012"},
{"$Name":"SliderGripper","$Type":"Slider","$Version":"2","ColorLeft":"&HFFFF9800","MaxValue":"180.0","MinValue":"0.0","ThumbPosition":"90.0","Width":"-2","Uuid":"1013"},
{"$Name":"HorizontalGripper","$Type":"HorizontalArrangement","$Version":"4","AlignHorizontal":"3","Width":"-2","Uuid":"1014","$Components":[
{"$Name":"ButtonOpen","$Type":"Button","$Version":"7","BackgroundColor":"&HFF2E7D32","FontBold":"True","FontSize":"16","Shape":"1","Text":"เปิดมือจับ","TextColor":"&HFFFFFFFF","Width":"-1048","Uuid":"1015"},
{"$Name":"ButtonClose","$Type":"Button","$Version":"7","BackgroundColor":"&HFFEF6C00","FontBold":"True","FontSize":"16","Shape":"1","Text":"หุบมือจับ","TextColor":"&HFFFFFFFF","Width":"-1048","Uuid":"1016"}]},
{"$Name":"ButtonReset","$Type":"Button","$Version":"7","BackgroundColor":"&HFF455A64","FontBold":"True","FontSize":"16","Shape":"1","Text":"กลับท่าเริ่มต้น (90°)","TextColor":"&HFFFFFFFF","Width":"-1096","Uuid":"1017"},
{"$Name":"LabelHelp","$Type":"Label","$Version":"5","FontSize":"13","HasMargins":"True","Text":"วิธีใช้: เปิดบลูทูธและตำแหน่ง (GPS) ของมือถือ ไม่ต้องจับคู่ในหน้าตั้งค่า กด '1. ค้นหา' รอ 3-5 วินาที แล้วกด '2. เลือกอุปกรณ์'","TextAlignment":"1","TextColor":"&HFF616161","Width":"-2","Uuid":"1018"},
{"$Name":"BluetoothLE1","$Type":"BluetoothLE","$Version":"20240822","NullTerminateStrings":"False","Uuid":"1019"},
{"$Name":"Notifier1","$Type":"Notifier","$Version":"6","Uuid":"1020"}
]}}
|#
