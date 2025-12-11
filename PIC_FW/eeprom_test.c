#include <pic.h>
#include "eeprom93c46.h"

extern void uart_puts(const char *s);
extern void uart_put_hex8(unsigned char v);

void eeprom_self_test(void)
{
    unsigned char addr = 0x10;  // test address
    unsigned char write_value = 0xA5;
    unsigned char read_value;

    uart_puts("EEPROM Test:\r\n");

    uart_puts("Writing 0xA5 to address 0x10...\r\n");
    ee_write_byte(addr, write_value);

    uart_puts("Reading address 0x10...\r\n");
    read_value = ee_read_byte(addr);

    uart_puts("Read value = 0x");
    uart_put_hex8(read_value);
    uart_puts("\r\n");

    if (read_value == write_value)
        uart_puts("EEPROM test OK!\r\n");
    else
        uart_puts("EEPROM test FAILED!\r\n");
}
