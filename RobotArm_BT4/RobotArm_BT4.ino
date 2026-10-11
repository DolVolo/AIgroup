// =====================================================================
// แขนกล 4 แกน ควบคุมผ่านบลูทูธ (Arduino Nano + โมดูลบลูทูธ + เซอร์โว 4 ตัว)
//
// แอปบนมือถือจะส่งคำสั่งมาเป็นข้อความ 1 บรรทัด รูปแบบ "S<เลขเซอร์โว>:<องศา>"
// เช่น "S1:120" = สั่งเซอร์โวตัวที่ 1 ไปที่ 120 องศา (ปิดท้ายด้วย \n)
//   S1 = ฐานหมุน   S2 = ไหล่   S3 = ข้อศอก   S4 = มือจับ
//
// ใช้ได้ทั้ง HC-06 (บลูทูธแบบ Classic) และโมดูล BLE แบบ HM-10 / AT-09 / BT05
// เพราะทั้งสองแบบส่งข้อความต่อมาที่ Nano ทางขา TX/RX เหมือนกัน
// =====================================================================

#include <Servo.h>           // ไลบรารีควบคุมเซอร์โว (มีมากับ Arduino IDE)
// พอร์ตอนุกรมจำลองสำหรับโมดูลบลูทูธ (อยู่ในโฟลเดอร์ src/ ของ sketch นี้ ไม่ต้องติดตั้งเพิ่ม)
// ใช้ NeoSWSerial แทน SoftwareSerial เพราะ SoftwareSerial ปิดอินเทอร์รัปต์ตลอดเวลาที่รับข้อมูล
// ทำให้สัญญาณเซอร์โวผิดเพี้ยน: เซอร์โวตัวอื่นกระตุกทุกครั้งที่ได้รับคำสั่งจากบลูทูธ
#include "src/NeoSWSerial/NeoSWSerial.h"

// สร้างตัวแปรเซอร์โว 4 ตัว
Servo servo1, servo2, servo3, servo4;

// การต่อสายโมดูลบลูทูธ:
//   TXD ของโมดูล -> ขา D10 ของ Nano (ขารับข้อมูล RX)
//   RXD ของโมดูล -> ขา D11 ของ Nano (ขาส่งข้อมูล TX)
// ใช้ D10/D11 แทน D0/D1 เพื่อไม่ให้ชนกับสาย USB ตอนอัปโหลดโค้ด
NeoSWSerial bluetooth(10, 11);

// เก็บองศาปัจจุบันของเซอร์โวแต่ละตัว (เริ่มต้นที่ 0 องศา ตอนเปิดบอร์ด ให้ตรงกับแอป)
int angle1 = 0;  // ฐานหมุน
int angle2 = 0;  // ไหล่
int angle3 = 0;  // ข้อศอก
int angle4 = 0;  // มือจับ

// องศาสูงสุดที่ข้อต่อแต่ละตัวขยับได้จริง ถ้าสั่งเกินนี้เซอร์โวจะดันชนโครง/ตัวกั้น
// กินไฟมากจน Nano รีสตาร์ท และเซอร์โวร้อนจนเสียได้ จึงจำกัดไว้
const int BASE_MAX = 180;     // ฐานหมุน (D3)
const int SHOULDER_MAX = 100; // ไหล่ (D5)
const int ELBOW_MAX = 100;    // ข้อศอก (D6)
const int GRIP_MAX = 50;      // มือจับ (D9): 0° = หุบ, ~45° = เปิด

// setup() ทำงานครั้งเดียวตอนเปิดบอร์ดหรือกดรีเซ็ต
void setup() {
  // ผูกเซอร์โวแต่ละตัวเข้ากับขา PWM ของ Nano
  servo1.attach(3);  // ฐานหมุน -> D3
  servo2.attach(5);  // ไหล่     -> D5
  servo3.attach(6);  // ข้อศอก   -> D6
  servo4.attach(9);  // มือจับ   -> D9

  // สั่งเซอร์โวทุกตัวไปที่ตำแหน่งเริ่มต้น
  servo1.write(angle1);
  servo2.write(angle2);
  servo3.write(angle3);
  servo4.write(angle4);

  bluetooth.begin(9600);  // ความเร็วสื่อสารกับโมดูลบลูทูธ (ค่าเริ่มต้นของโมดูลคือ 9600)
  Serial.begin(9600);     // พอร์ต USB สำหรับดูข้อมูลใน Serial Monitor
  Serial.println("Ready - type S1:120 here to test without phone");
}

// ส่งคำสั่ง AT ไปที่โมดูลบลูทูธ แล้วอ่านคำตอบ (โมดูลที่ทำงานปกติจะตอบ "OK")
String sendAt(NeoSWSerial &port, uint16_t rate) {
  port.begin(rate);  // begin() เริ่มรับข้อมูลจากพอร์ตนี้ด้วย
  delay(100);
  while (port.available()) port.read();  // ล้างข้อมูลค้าง

  String reply = "";
  const char* variants[] = {"AT", "AT\r\n"};  // HM-10 ใช้ "AT" เปล่าๆ, BT05/JDY ใช้ "AT\r\n"
  for (int v = 0; v < 2 && reply.length() == 0; v++) {
    port.print(variants[v]);
    unsigned long t = millis();
    while (millis() - t < 1200) {
      while (port.available()) {
        char ch = port.read();
        if (reply.length() < 48) reply += ch;  // เก็บแค่ 48 ตัวอักษร กันหน่วยความจำ Nano (2KB) เต็มจนบอร์ดค้าง
      }
    }
  }
  return reply;
}

// คำสั่ง SCAN (พิมพ์ใน Serial Monitor): ทดสอบโมดูลบลูทูธทุกความเร็ว และทั้งสองทิศทางของสาย
// ต้องตัดการเชื่อมต่อมือถือก่อน (ไฟโมดูลกะพริบ) เพราะตอนเชื่อมต่ออยู่โมดูลจะไม่ตอบคำสั่ง AT
// NeoSWSerial รองรับเฉพาะความเร็ว 9600, 19200 และ 38400
const uint16_t scanRates[] = {9600, 19200, 38400};
const int scanRateCount = sizeof(scanRates) / sizeof(scanRates[0]);

void scanPort(NeoSWSerial &port, const char* label) {
  for (int i = 0; i < scanRateCount; i++) {
    String reply = sendAt(port, scanRates[i]);
    Serial.print(label);
    Serial.print(scanRates[i]);
    Serial.print(" baud: ");
    if (reply.length() == 0) Serial.println("(no reply)");
    else { Serial.print("REPLY ["); Serial.print(reply.length()); Serial.print("] "); Serial.println(reply); }
  }
}

void scanBluetooth() {
  Serial.println("SCAN: phone must be DISCONNECTED (module LED blinking)");
  scanPort(bluetooth, "  TXD->D10 ");
  {
    // พอร์ตแบบสลับขา สร้างชั่วคราวเฉพาะตอนทดสอบ (เผื่อต่อสาย TX/RX สลับกัน)
    NeoSWSerial swapped(11, 10);
    scanPort(swapped, "  TXD->D11 ");
    swapped.ignore();  // ต้องหยุดรับข้อมูลก่อนตัวแปรนี้ถูกลบ ไม่งั้นอินเทอร์รัปต์จะเรียกตัวแปรที่ไม่มีอยู่แล้ว
  }
  // กลับไปใช้พอร์ตปกติ: D10 = รับข้อมูล, D11 = ส่งข้อมูล (begin() ตั้งค่าขาให้ใหม่)
  bluetooth.begin(9600);
  Serial.println("SCAN done");
}

// รับคำสั่ง 1 บรรทัด แล้วสั่งเซอร์โวตัวที่ตรงกัน
// ใช้ร่วมกันทั้งคำสั่งจากบลูทูธและคำสั่งที่พิมพ์ใน Serial Monitor
void handleCommand(String command, const char* source) {
  // แสดงข้อความดิบที่ได้รับ (ก่อนกรอง) พร้อมความยาว เพื่อดูว่ามีตัวอักษรแปลกปนมาหรือไม่
  Serial.print(source);
  Serial.print(" raw [");
  Serial.print(command.length());
  Serial.print("]: ");
  Serial.println(command);

  command.trim();  // ตัดช่องว่างและตัวขึ้นบรรทัด (\r) หัวท้ายทิ้ง

  if (command.equalsIgnoreCase("SCAN")) {  // คำสั่งทดสอบโมดูลบลูทูธ
    scanBluetooth();
    return;
  }

  // คำสั่ง AT ที่พิมพ์ใน Serial Monitor จะถูกส่งต่อให้โมดูลบลูทูธ แล้วแสดงคำตอบ
  // (ใช้ตรวจ/ตั้งค่าโมดูล เช่น AT+VERSION, AT+UUID ต้องตัดการเชื่อมต่อมือถือก่อน)
  if (strcmp(source, "USB") == 0 && command.startsWith("AT")) {
    while (bluetooth.available()) bluetooth.read();
    bluetooth.print(command);
    bluetooth.print("\r\n");
    String reply = "";
    unsigned long t = millis();
    while (millis() - t < 1500) {
      while (bluetooth.available()) {
        char ch = bluetooth.read();
        if (reply.length() < 48) reply += ch;  // เก็บแค่ 48 ตัวอักษร กันหน่วยความจำเต็ม
      }
    }
    Serial.print("  -> module reply: ");
    Serial.println(reply.length() ? reply : "(no reply)");
    return;
  }

  // ตัดตัวอักษรขยะที่อาจติดมาข้างหน้า (เช่น \0 จากโมดูล BLE) ให้เริ่มที่ตัว 'S'
  int start = command.indexOf('S');
  if (start < 0) {
    Serial.println("  -> ignored (no 'S')");
    return;
  }
  command = command.substring(start);

  // ตรวจรูปแบบให้ครบก่อนสั่งเซอร์โว: S + เลข 1-4 + ':' + ตัวเลข 1-3 หลัก เช่น "S2:45"
  // ข้อความที่เสียระหว่างทาง (เช่น "S1:" ที่ตัวเลขหาย) จะถูกข้าม แทนที่จะสั่งเซอร์โวไป 0°
  bool valid = command.length() >= 4 && command.length() <= 6
               && command.charAt(1) >= '1' && command.charAt(1) <= '4'
               && command.charAt(2) == ':';
  for (unsigned int k = 3; valid && k < command.length(); k++) {
    if (!isDigit(command.charAt(k))) valid = false;
  }
  if (!valid) {
    Serial.println("  -> ignored (bad format)");
    return;
  }

  // ดูว่าเป็นคำสั่งของเซอร์โวตัวไหน แล้วอ่านตัวเลของศาหลัง "Sx:"
  // substring(3) = ข้อความตั้งแต่ตัวที่ 4 เป็นต้นไป (ตัวเลของศา)
  // constrain(..., 0, MAX) = บังคับให้อยู่ในช่วงที่ข้อต่อขยับได้จริง กันเซอร์โวเสียหาย
  if (command.startsWith("S1:")) {         // ฐานหมุน
    angle1 = constrain(command.substring(3).toInt(), 0, BASE_MAX);
    servo1.write(angle1);
    Serial.print("  -> servo 1 (D3) = "); Serial.println(angle1);
  }
  else if (command.startsWith("S2:")) {    // ไหล่
    angle2 = constrain(command.substring(3).toInt(), 0, SHOULDER_MAX);
    servo2.write(angle2);
    Serial.print("  -> servo 2 (D5) = "); Serial.println(angle2);
  }
  else if (command.startsWith("S3:")) {    // ข้อศอก
    angle3 = constrain(command.substring(3).toInt(), 0, ELBOW_MAX);
    servo3.write(angle3);
    Serial.print("  -> servo 3 (D6) = "); Serial.println(angle3);
  }
  else if (command.startsWith("S4:")) {    // มือจับ
    angle4 = constrain(command.substring(3).toInt(), 0, GRIP_MAX);
    servo4.write(angle4);
    Serial.print("  -> servo 4 (D9) = "); Serial.println(angle4);
  }
  else {
    Serial.println("  -> ignored (unknown command)");  // คำสั่งที่ไม่ตรงรูปแบบจะถูกข้ามไป
  }
}

// อ่านข้อความ 1 บรรทัด (จนถึง \n หรือไม่มีข้อมูลเข้ามา 200 ms) เก็บไม่เกิน 32 ตัวอักษร
// คำสั่งจริงยาวแค่ ~7 ตัว ถ้ามีข้อมูลขยะไหลเข้ามามาก หน่วยความจำ Nano (2KB) จะไม่เต็มจนบอร์ดค้าง
String readLine(Stream &port) {
  String line = "";                        // ตัวแปรเก็บบรรทัดที่กำลังอ่าน เริ่มต้นว่างเปล่า
  unsigned long last = millis();           // จำเวลาล่าสุดที่ได้รับตัวอักษร ใช้นับว่าไม่มีข้อมูลเข้ามานานแค่ไหนแล้ว
  while (millis() - last < 200) {          // วนอ่านไปเรื่อยๆ จนกว่าจะเงียบ (ไม่มีข้อมูลเข้า) ครบ 200 มิลลิวินาที
    if (port.available()) {                // ถ้ามีไบต์ใหม่รอให้อ่านอยู่ในพอร์ตนี้
      char ch = port.read();               // อ่านไบต์นั้นออกมา 1 ตัว
      if (ch == '\n') break;               // เจอตัวขึ้นบรรทัดใหม่ แปลว่าจบ 1 บรรทัดแล้ว ออกจากลูปทันที
      if (line.length() < 32) line += ch;  // ต่อตัวอักษรเข้า line ถ้ายังไม่เกิน 32 ตัว (กันกินหน่วยความจำจนบอร์ดค้าง)
      last = millis();                     // มีข้อมูลเข้ามา รีเซ็ตตัวจับเวลาเงียบใหม่
    }
  }
  return line;                             // ส่งบรรทัดที่อ่านได้กลับไป (อาจเป็นบรรทัดไม่สมบูรณ์ถ้าหมดเวลาก่อนเจอ \n)
}

// loop() ทำงานวนซ้ำตลอดเวลาไม่มีที่สิ้นสุด ตราบใดที่บอร์ดยังมีไฟเลี้ยง
void loop() {
  // คำสั่งจากบลูทูธ (แอปบนมือถือ) อ่านจนถึงตัวขึ้นบรรทัดใหม่ (\n) เช่น "S1:120"
  if (bluetooth.available()) {                      // ตรวจว่ามีข้อมูลใหม่ส่งเข้ามาทางบลูทูธรออยู่หรือไม่
    handleCommand(readLine(bluetooth), "BT");       // อ่านมาทีละบรรทัด แล้วส่งไปแปลคำสั่งและสั่งเซอร์โว (ระบุแหล่งที่มาว่า "BT")
  }
  // คำสั่งที่พิมพ์ใน Serial Monitor (ใช้ทดสอบเซอร์โวโดยไม่ต้องใช้มือถือ)
  if (Serial.available()) {                         // ตรวจว่ามีข้อความใหม่พิมพ์เข้ามาทาง USB Serial Monitor หรือไม่
    handleCommand(readLine(Serial), "USB");         // อ่านมาทีละบรรทัด แล้วส่งไปแปลคำสั่งเหมือนกัน (ระบุแหล่งที่มาว่า "USB")
  }
}
