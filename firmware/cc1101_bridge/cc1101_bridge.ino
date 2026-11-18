/**
 * cc1101_bridge — firmware contoh untuk FlipperDroid Sub-GHz
 *
 * Board   : ESP32 DevKit (Arduino framework)
 * Library : "SmartRC-CC1101-Driver-Lib" (ELECHOUSE) — install lewat Library Manager
 *           https://github.com/LSatan/SmartRC-CC1101-Driver-Lib
 *
 * Wiring CC1101 -> ESP32
 *   VCC   3V3        (JANGAN 5V)
 *   GND   GND
 *   SCK   18
 *   MISO  19
 *   MOSI  23
 *   CSN   5
 *   GDO0  4          (data async serial, dua arah)
 *   GDO2  2          (tidak dipakai, boleh dilepas)
 *
 * Protokol (ASCII, 115200 8N1), satu perintah per baris:
 *   PING              -> PONG cc1101-bridge 1
 *   FREQ <hz>         -> OK freq <mhz>
 *   RX                -> OK rx      lalu stream "RX <durasi,durasi,...>"
 *   IDLE              -> OK idle
 *   TX <durasi,...>   -> OK tx
 *
 * Durasi dalam mikrodetik, konvensi Flipper: positif = carrier ON, negatif = OFF.
 *
 * CATATAN: ini capture/replay raw (seperti "Read Raw" di Flipper), bukan
 * decoder protokol. Polaritas level GDO0 bisa berbeda tergantung modul;
 * kalau replay tidak memicu perangkat, coba balik tandanya.
 */

#include <Arduino.h>
#include <ELECHOUSE_CC1101_SRC_DRV.h>

#define PIN_CSN   5
#define PIN_GDO0  4
#define PIN_SCK   18
#define PIN_MISO  19
#define PIN_MOSI  23

// Register CC1101 yang dipakai (lihat datasheet, jangan diubah sembarangan)
#define REG_IOCFG0    0x02
#define REG_PKTCTRL0  0x08

#define MAX_EDGES     1024
#define MAX_TX_STEPS  1024
#define EDGE_SILENCE_US 15000UL

static float g_mhz = 433.92f;
static bool g_rx = false;

static volatile uint32_t edgeUs[MAX_EDGES];
static volatile uint16_t edgeCount = 0;
static volatile uint32_t lastEdgeUs = 0;
static portMUX_TYPE edgeMux = portMUX_INITIALIZER_UNLOCKED;

static int32_t txBuffer[MAX_TX_STEPS];

void IRAM_ATTR onEdge() {
  uint32_t now = micros();
  uint32_t delta = now - lastEdgeUs;
  lastEdgeUs = now;
  portENTER_CRITICAL_ISR(&edgeMux);
  if (edgeCount < MAX_EDGES) {
    edgeUs[edgeCount++] = delta;
  }
  portEXIT_CRITICAL_ISR(&edgeMux);
}

void setFrequency(float mhz) {
  g_mhz = mhz;
  ELECHOUSE_cc1101.setMHZ(mhz);
}

void stopRx() {
  if (!g_rx) return;
  detachInterrupt(digitalPinToInterrupt(PIN_GDO0));
  ELECHOUSE_cc1101.SetIdle();
  g_rx = false;
}

void enterRx() {
  stopRx();
  ELECHOUSE_cc1101.SetRx();
  // Async serial mode: demodulated OOK/FSK keluar di GDO0
  ELECHOUSE_cc1101.SpiWriteReg(REG_PKTCTRL0, 0x32);
  ELECHOUSE_cc1101.SpiWriteReg(REG_IOCFG0, 0x0D);

  portENTER_CRITICAL(&edgeMux);
  edgeCount = 0;
  portEXIT_CRITICAL(&edgeMux);
  lastEdgeUs = micros();

  attachInterrupt(digitalPinToInterrupt(PIN_GDO0), onEdge, CHANGE);
  g_rx = true;
}

/** Cetak frame kalau sudah tidak ada edge selama EDGE_SILENCE_US. */
void flushFrame() {
  if (!g_rx) return;

  uint16_t count;
  uint32_t durations[MAX_EDGES];

  portENTER_CRITICAL(&edgeMux);
  count = edgeCount;
  if (count > 0) {
    for (uint16_t i = 0; i < count; i++) durations[i] = edgeUs[i];
    edgeCount = 0;
  }
  portEXIT_CRITICAL(&edgeMux);

  if (count < 4) return;

  Serial.print("RX ");
  for (uint16_t i = 0; i < count; i++) {
    if (i > 0) Serial.print(',');
    // Konvensi: interval pertama dianggap mark (positif).
    int32_t value = (int32_t)durations[i];
    Serial.print((i % 2 == 0) ? value : -value);
  }
  Serial.println();
}

void transmit(const int32_t *steps, int count) {
  detachInterrupt(digitalPinToInterrupt(PIN_GDO0));
  g_rx = false;

  pinMode(PIN_GDO0, OUTPUT);
  digitalWrite(PIN_GDO0, LOW);

  ELECHOUSE_cc1101.SetTx();
  delay(2);

  for (int i = 0; i < count; i++) {
    uint32_t duration = (uint32_t)labs((long)steps[i]);
    if (duration == 0) duration = 1;
    digitalWrite(PIN_GDO0, steps[i] > 0 ? HIGH : LOW);
    delayMicroseconds(duration);
  }

  digitalWrite(PIN_GDO0, LOW);
  delayMicroseconds(500);
  ELECHOUSE_cc1101.SetIdle();
  pinMode(PIN_GDO0, INPUT);
}

int parseCsv(const String &payload, int32_t *out, int maxItems) {
  int count = 0;
  int start = 0;
  while (start < (int)payload.length() && count < maxItems) {
    int comma = payload.indexOf(',', start);
    if (comma < 0) comma = payload.length();
    String token = payload.substring(start, comma);
    token.trim();
    if (token.length() > 0) {
      out[count++] = (int32_t)token.toInt();
    }
    start = comma + 1;
  }
  return count;
}

void handleCommand(String line) {
  line.trim();
  if (line.length() == 0) return;

  if (line == "PING") {
    Serial.println("PONG cc1101-bridge 1");
    return;
  }

  if (line.startsWith("FREQ ")) {
    long hz = line.substring(5).toInt();
    if (hz < 300000000L || hz > 928000000L) {
      Serial.println("ERR freq di luar 300-928 MHz");
      return;
    }
    setFrequency((float)hz / 1000000.0f);
    Serial.print("OK freq ");
    Serial.println(g_mhz, 4);
    return;
  }

  if (line == "RX") {
    enterRx();
    Serial.println("OK rx");
    return;
  }

  if (line == "IDLE") {
    stopRx();
    Serial.println("OK idle");
    return;
  }

  if (line.startsWith("TX ")) {
    stopRx();
    int count = parseCsv(line.substring(3), txBuffer, MAX_TX_STEPS);
    if (count < 2) {
      Serial.println("ERR data tx kosong");
      return;
    }
    transmit(txBuffer, count);
    Serial.print("OK tx ");
    Serial.println(count);
    return;
  }

  Serial.print("ERR perintah tidak dikenal: ");
  Serial.println(line);
}

void setup() {
  Serial.begin(115200);
  delay(200);

  pinMode(PIN_GDO0, INPUT);

  ELECHOUSE_cc1101.setSpiPin(PIN_SCK, PIN_MISO, PIN_MOSI, PIN_CSN);
  ELECHOUSE_cc1101.Init();
  ELECHOUSE_cc1101.setModulation(2);   // 2 = OOK (ASK)
  ELECHOUSE_cc1101.setMHZ(g_mhz);
  ELECHOUSE_cc1101.SetRx();

  Serial.println("READY cc1101-bridge 1");
}

void loop() {
  static String line;

  while (Serial.available() > 0) {
    char c = (char)Serial.read();
    if (c == '\n') {
      handleCommand(line);
      line = "";
    } else if (c != '\r' && line.length() < 20000) {
      line += c;
    }
  }

  if (g_rx && edgeCount > 0 && (micros() - lastEdgeUs) > EDGE_SILENCE_US) {
    flushFrame();
  }
}
