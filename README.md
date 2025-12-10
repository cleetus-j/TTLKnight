This is just some sloppy code that would work with an Arduino Nano based TTL circuit tester that I devised to use for testing retro memory mapper circuits. A simple java program with integrated help for moving around easily. The connection works and commanding the device as well.
I have not tried the file commands. I thought giving the ability to work based on script files are good, as some commands are not present on the Nano, or the connected PIC controller either, such as timing, loops and simple code tasks. Don't expect fantastic code quality, this one is made with Deepseek, as I wanted something quick and dirty that works.
This should work with java 8 as well. As I use Linux, I tried to cater to this OS, so you need to select the right device in Linux style before connecting to the tester, such as /dev/ttyUSB0, then connect, and give commands directly. As I said, there's a help menu with commands, how to use the script so I won't clutter here.

Some info about the TTL circuit in advance:
  -It's based on an Arduino Nano. Mostly for the USB<-->TTL bridge. You can connect to it, and command it directly, this program is for minor convenience only.
  -The Nano controls an Intel 8255-2. This gives a few extra TTL lines.(2 8-bit ports and 2 4-bit ports, depends on the config.)
  -The Nano also controls via Software UART, a PIC16F877. This one also gives a lot of extra pins. The PIC however is only set to receive commands, and you are not able with the current program to read from the ports. This is really made to be used as an output.
  -The PIC has a 4Mhz clock, since its not really doing any heavy lifting, just read the serial port, and do what they tell the controller.

  Automatic things on both controllers:
    -The Nano has a 4-bit counter on the 8255's Port A's lower nibble. This can be of course turned off. I thought it would be good to see some immediate feedback from the controller, and test it's functions. On my board this lower nibble has LEDs on it.
    -The PIC has a Knight Rider style chaser light on its Port D. This can also be stopped and used as how the user would see fit. This chaser light gave the name for the project, as the PIC's code was done first.
