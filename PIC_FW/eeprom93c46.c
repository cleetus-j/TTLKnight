#include <pic.h>
#include "eeprom93c46.h"
#define _XTAL_FREQ 4000000UL    // 4 MHz crystal

// Clock pulse
static void ee_clock(void)
{
    EE_SK = 1;
    __delay_us(5);
    EE_SK = 0;
    __delay_us(5);
}

void ee_init(void)
{
    ADCON1 = 0x06;   // Make PORTA digital

    EE_DI_TRIS = 0;  // DI output
    EE_DO_TRIS = 1;  // DO input
    EE_SK_TRIS = 0;  // SK output
    EE_CS_TRIS = 0;  // CS output

    EE_DI = 0;
    EE_SK = 0;
    EE_CS = 1;
}

static void ee_send_bits(unsigned int data, unsigned char count)
{
    while (count--)
    {
        EE_DI = (data & (1 << count)) ? 1 : 0;
        ee_clock();
    }
}

unsigned char ee_read_byte(unsigned char address)
{
    unsigned char i, result = 0;

    EE_CS = 1;
    // Start (1), opcode READ (10), address(6-bit)
    ee_send_bits(0x600 | address, 9);

    // Read 8 bits
    for (i = 0; i < 8; i++)
    {
        ee_clock();
        result <<= 1;
        if (EE_DO) result |= 1;
    }

    EE_CS = 0;
    return result;
}

static void ee_write_start(void)
{
    EE_CS = 1;
    ee_send_bits(0x300, 3); // Start + EWEN command prefix
    EE_CS = 0;
}

void ee_write_enable(void)
{
    EE_CS = 1;
    ee_send_bits(0x980, 9); // EWEN sequence
    EE_CS = 0;
}

void ee_write_disable(void)
{
    EE_CS = 1;
    ee_send_bits(0x000, 9); // EWDS sequence
    EE_CS = 0;
}

void ee_write_byte(unsigned char address, unsigned char data)
{
    unsigned char i;

    ee_write_enable();

    EE_CS = 1;
    ee_send_bits(0x500 | address, 9); // WRITE opcode (01) + address
    ee_send_bits(data, 8);            // write data
    EE_CS = 0;

    // Wait for write cycle (DO goes high)
    EE_CS = 1;
    while (!EE_DO) ;
    EE_CS = 0;

    ee_write_disable();
}
