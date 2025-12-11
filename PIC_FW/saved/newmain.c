#include <pic.h>
#include <string.h>

//-------------------------------------------------------
//  CONFIG
//-------------------------------------------------------
__CONFIG(FOSC_XT & WDTE_OFF & PWRTE_ON & BOREN_ON & LVP_OFF & CP_OFF);

//-------------------------------------------------------
//  GLOBALS
//-------------------------------------------------------
volatile unsigned long ms = 0;

unsigned char greeted = 0;
unsigned long last_rx_time = 0;

#define RX_BUF_SIZE 32
char rx_buf[RX_BUF_SIZE];
unsigned char rx_pos = 0;

// Knight rider state
unsigned char pattern = 0x01;
unsigned char direction = 0;      // 0 = left, 1 = right
unsigned char run_chaser = 1;     // 1 = running, 0 = stopped
unsigned long next_update = 0;
unsigned long interval = 120;     // default speed (ms)
// Convert a single hex digit into value (0?15), or -1 if invalid
int hex_digit(char c)
{
    if (c >= '0' && c <= '9') return c - '0';
    if (c >= 'A' && c <= 'F') return c - 'A' + 10;
    if (c >= 'a' && c <= 'f') return c - 'a' + 10;
    return -1;
}

// Parse binary string "10100101"
int parse_binary(const char *s)
{
    int v = 0;
    while (*s == '0' || *s == '1')
    {
        v = (v << 1) | (*s - '0');
        s++;
    }
    return v;
}

// Parse hex string like "3F" or "0x3F"
int parse_hex(const char *s)
{
    int v = 0;
    int d;

    // optional 0x or 0X
    if (s[0] == '0' && (s[1] == 'x' || s[1] == 'X'))
        s += 2;

    while ((d = hex_digit(*s)) >= 0)
    {
        v = (v << 4) | d;
        s++;
    }
    return v;
}

//-------------------------------------------------------
//  TIMER0 ISR (1ms tick)
//-------------------------------------------------------
void interrupt isr(void)
{
    if (T0IF)
    {
        T0IF = 0;
        ms++;
        TMR0 = 225;
    }
}

//-------------------------------------------------------
void timer0_init(void)
{
    T0CS = 0;
    PSA  = 0;
    PS2=1; PS1=0; PS0=0;   // 1:32
    TMR0 = 225;
    T0IF = 0;
    T0IE = 1;
    GIE  = 1;
}

//-------------------------------------------------------
void uart_init(void)
{
    SPBRG = 25;
    BRGH = 1;

    SYNC = 0;
    SPEN = 1;

    TXEN = 1;
    CREN = 1;
}

//-------------------------------------------------------
void uart_putc(char c)
{
    while (!TXIF);
    TXREG = c;
}

void uart_puts(const char *s)
{
    while (*s)
        uart_putc(*s++);
}

int uart_rx_ready(void)
{
    return RCIF;
}

//-------------------------------------------------------
//  PARSE COMMANDS
//-------------------------------------------------------
long parse_number(const char *s)
{
    long v = 0;
    while (*s >= '0' && *s <= '9')
    {
        v = v * 10 + (*s - '0');
        s++;
    }
    return v;
}

void do_help(void)
{
    uart_puts("Commands:\r\n");
    uart_puts("  stop        - freeze chaser\r\n");
    uart_puts("  start       - resume chaser\r\n");
    uart_puts("  speed <ms>  - set speed in ms\r\n");
    uart_puts("  left        - force left direction\r\n");
    uart_puts("  right       - force right direction\r\n");
    uart_puts("  help        - show this help\r\n");
}

void process_message(void)
{
    // STOP
    if (strcmp(rx_buf, "stop") == 0)
    {
        run_chaser = 0;
        uart_puts("chaser stopped\r\n");
    }
    // START
    else if (strcmp(rx_buf, "start") == 0)
    {
        run_chaser = 1;
        uart_puts("chaser resumed\r\n");
    }
    // LEFT
    else if (strcmp(rx_buf, "left") == 0)
    {
        direction = 0;
        uart_puts("direction = left\r\n");
    }
    // RIGHT
    else if (strcmp(rx_buf, "right") == 0)
    {
        direction = 1;
        uart_puts("direction = right\r\n");
    }
    // HELP
    else if (strcmp(rx_buf, "help") == 0)
    {
        do_help();
    }
    // SPEED
    else if (strncmp(rx_buf, "speed ", 6) == 0)
    {
        long v = parse_number(rx_buf + 6);
        if (v > 10 && v < 2000)
        {
            interval = v;
            uart_puts("speed set to ");
            uart_puts(rx_buf + 6);
            uart_puts(" ms\r\n");
        }
        else
        {
            uart_puts("invalid speed\r\n");
        }
    }
    // UNKNOWN
    // SET command: binary, hex, or decimal
else if (strncmp(rx_buf, "set ", 4) == 0)
{
    const char *arg = rx_buf + 4;
    unsigned char value = 0;

    // binary: b10100101
    if (arg[0] == 'b' || arg[0] == 'B')
    {
        value = parse_binary(arg + 1);
    }
    // hex: 0x3F or 3F
    else if ((arg[0] == '0' && (arg[1] == 'x' || arg[1] == 'X')) ||
             hex_digit(arg[0]) >= 0)
    {
        value = parse_hex(arg);
    }
    // decimal number
    else if (arg[0] >= '0' && arg[0] <= '9')
    {
        value = (unsigned char)parse_number(arg);
    }
    else
    {
        uart_puts("invalid set value\r\n");
        goto done;
    }

    // Apply value to PORTD
    PORTD = value;

    uart_puts("PORTD set to 0x");
    // print hex value
    const char hexchars[] = "0123456789ABCDEF";
    uart_putc(hexchars[(value >> 4) & 0x0F]);
    uart_putc(hexchars[value & 0x0F]);
    uart_puts("\r\n");

done:
    ;
}

    else
    {
        uart_puts("message was: ");
        uart_puts(rx_buf);
        uart_puts("\r\n");
    }

    rx_pos = 0;
    rx_buf[0] = '\0';
}

//-------------------------------------------------------
//  UART TASK (non-blocking)
//-------------------------------------------------------
void uart_task(void)
{
    if (uart_rx_ready())
    {
        char c = RCREG;
        last_rx_time = ms;

        if (!greeted)
        {
            uart_puts("\r\nConnection detected.\r\n");
            greeted = 1;
        }

        // Newline (Enter)
        if (c == '\r' || c == '\n')
        {
            uart_puts("\r\n");
            if (rx_pos > 0)
            {
                rx_buf[rx_pos] = '\0';
                process_message();
            }
            rx_pos = 0;
            return;
        }

        // Backspace
        if (c == 8)
        {
            if (rx_pos > 0)
            {
                rx_pos--;
                uart_putc(8); uart_putc(' '); uart_putc(8);
            }
            return;
        }

        // Normal character
        if (rx_pos < RX_BUF_SIZE - 1)
        {
            rx_buf[rx_pos++] = c;
            uart_putc(c);  // echo
        }
    }
    else
    {
        if (greeted && (ms - last_rx_time > 5000))
            greeted = 0;
    }
}

//-------------------------------------------------------
//  MAIN
//-------------------------------------------------------
void main(void)
{
    TRISD = 0x00;
    PORTD = 0x00;

    timer0_init();
    uart_init();

    uart_puts("\r\nPIC16F877 Ready.\r\nKnight Rider Command Mode.\r\nType 'help' for commands.\r\n");

    while (1)
    {
        // KNIGHT RIDER (non-blocking)
        if (run_chaser && ms >= next_update)
        {
            next_update = ms + interval;

            PORTD = pattern;

            if (!direction)
            {
                pattern <<= 1;
                if (pattern == 0x80) direction = 1;
            }
            else
            {
                pattern >>= 1;
                if (pattern == 0x01) direction = 0;
            }
        }

        // UART
        uart_task();
    }
}
