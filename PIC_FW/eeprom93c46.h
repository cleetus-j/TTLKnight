#ifndef EEPROM93C46_H
#define EEPROM93C46_H

// Pin assignments for MicroWire EEPROM
#define EE_DI   RA0     // DI
#define EE_DO   RA1     // DO
#define EE_SK   RA2     // SK
#define EE_CS   RA3     // CS

#define EE_DI_TRIS  TRISA0
#define EE_DO_TRIS  TRISA1
#define EE_SK_TRIS  TRISA2
#define EE_CS_TRIS  TRISA3

void ee_init(void);
void ee_write_enable(void);
void ee_write_disable(void);
void ee_write_byte(unsigned char address, unsigned char data);
unsigned char ee_read_byte(unsigned char address);

#endif
