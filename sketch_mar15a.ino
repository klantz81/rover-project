void setup() {
  Serial.begin(9600);
  for (int i = 4; i <= 7; i++) {
    pinMode(i, OUTPUT);
    digitalWrite(i, HIGH);
  }
}

void loop() {
  bool states[8];
  bool bytes_read = false;
  
  while (Serial.available() > 0) {
    int j = Serial.read();
    for (int i = 0; i < 8; i++) {
       states[i] = (j>>i)&1;  
    }
    bytes_read = true;
  }
  
  // pin 4 - relay 1 - forward
  // pin 5 - relay 2 - right
  // pin 6 - relay 3 - reverse
  // pin 7 - relay 4 - left
  if (bytes_read) {
    for (int i = 4; i <= 7; i++) {
      digitalWrite(i, states[i-4] ? HIGH : LOW);
    }
  }
}
