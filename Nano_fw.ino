
#include <SoftwareSerial.h>

// --- 8255 Pin Definitions ---
const int A0_cont = A0;
const int A1_cont = A1;
const int CS      = 12; // Chip Select (active LOW)
const int RD      = 11; // Read (active LOW)
const int WR      = 10; // Write (active LOW)
const int RES     = A2; // Reset

// --- LED Blink Definitions ---
const int ledPin = 13;         // Built-in LED
unsigned long previousMillis = 0;
const long interval = 500;     // 500ms toggle => 1s full cycle
int ledState = LOW;

// --- Counter Variables ---
int ledCounter = 0;            // 0..15

// --- UART Settings ---
const unsigned long PIC_BAUD = 9600;

// --- SoftwareSerial on A3 (RX) and A4 (TX) ---
const uint8_t PIC_RX_PIN = A3; // Nano receives from PIC on A3 (D17)
const uint8_t PIC_TX_PIN = A4; // Nano sends to PIC on A4 (D18)
SoftwareSerial picSerial(PIC_RX_PIN, PIC_TX_PIN); // RX, TX

// ---- 8255 write function (same as your logic, with short /CS pulse) ----
void write8255(int address_select, byte data) {
  // Set A0/A1 (00=PortA, 01=PortB, 10=PortC, 11=Control)
  digitalWrite(A0_cont, address_select & 0b01);
  digitalWrite(A1_cont, address_select & 0b10);

  // Output data to D0-D7 (Pins 2-9)
  for (int i = 0; i < 8; i++) {
    digitalWrite(i + 2, (data >> i) & 0x01);
  }

  // Select 8255 only for the strobe, then deselect again
  digitalWrite(CS, LOW);   // select 8255 (active LOW)
  digitalWrite(WR, LOW);   // strobe
  digitalWrite(WR, HIGH);
  digitalWrite(CS, HIGH);  // deselect 8255
}

void setup() {
  // --- 8255 pins ---
  for (int i = 2; i <= 9; i++) pinMode(i, OUTPUT); // Data Bus (D2-D9)
  pinMode(WR, OUTPUT);
  pinMode(RD, OUTPUT);
  pinMode(CS, OUTPUT);
  pinMode(A0_cont, OUTPUT);
  pinMode(A1_cont, OUTPUT);
  pinMode(RES, OUTPUT);

  // Idle states
  digitalWrite(CS, HIGH);  // keep 8255 deselected by default
  digitalWrite(WR, HIGH);
  digitalWrite(RD, HIGH);

  // Hardware Reset the 8255
  digitalWrite(RES, HIGH); delay(10);
  digitalWrite(RES, LOW);  delay(10);

  // Configure 8255 (Mode 0, All Ports Output): 0x80
  write8255(3, 0x80);

  // LED
  pinMode(ledPin, OUTPUT);

  // --- USB serial to PC ---
  Serial.begin(PIC_BAUD);     // Use 9600 on the PC side for convenience too
  while (!Serial) { /* On Nano, this returns immediately */ }

  // --- SoftwareSerial to PIC ---
  picSerial.begin(PIC_BAUD);
  // Optional small delay to let PIC boot
  delay(100);

  Serial.println(F("Bridge ready: PC <-> Nano(USB) | Nano(A3/A4) <-> PIC @ 9600"));
  Serial.println(F("Type commands like: set D 3F"));
}

void loop() {
  // --- Your LED/counter logic (unchanged) ---
  unsigned long currentMillis = millis();
  if (currentMillis - previousMillis >= interval) {
    previousMillis = currentMillis;
    ledState = !ledState;
    digitalWrite(ledPin, ledState);

    if (ledState == HIGH) {
      // Write the current count to 8255 Port A (Address 0)
      write8255(0, ledCounter);
      ledCounter++;
      if (ledCounter > 15) ledCounter = 0;
    }
  }

  // --- Bridge PC -> PIC (ASCII, e.g., "set D 3F\n") ---
  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    picSerial.write(c);      // forward to PIC
    // Optional: echo back to PC for local feedback
    // Serial.write(c);
  }

  // --- Bridge PIC -> PC (reply/ack) ---
  while (picSerial.available() > 0) {
    char c = (char)picSerial.read();
    Serial.write(c);         // forward PIC reply to PC
  }
}
