package org.example;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Arduino Script Processor with flow control, baud rate selection, and help system
 */
public class Main {
    // Supported baud rates
    private static final int[] SUPPORTED_BAUD_RATES = {
            300, 1200, 2400, 4800, 9600, 14400, 19200, 28800,
            38400, 57600, 115200, 230400, 250000, 500000, 1000000
    };

    private static final int DEFAULT_BAUD_RATE = 9600;
    private int currentBaudRate = DEFAULT_BAUD_RATE;
    private static final String VERSION = "1.3.0";

    private final Scanner scanner = new Scanner(System.in);

    // Serial communication
    private Object serialPort;
    private InputStream serialIn;
    private OutputStream serialOut;
    private final boolean connected = false;
    private Thread readerThread;

    // Script processing
    private final Map<String, Integer> labels = new HashMap<>();
    private final Map<String, String> variables = new HashMap<>();
    private final Stack<Integer> callStack = new Stack<>();
    private final boolean stopExecution = false;

    // Regex patterns
    private static final Pattern LABEL_PATTERN = Pattern.compile("^(\\d+)->(\\w+)$");
    private static final Pattern GOTO_PATTERN = Pattern.compile("^GOTO\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WAIT_PATTERN = Pattern.compile("^WAIT\\s+(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SET_PATTERN = Pattern.compile("^SET\\s+(\\w+)\\s*=\\s*(.+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern IF_PATTERN = Pattern.compile("^IF\\s+(.+)\\s+GOTO\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOP_PATTERN = Pattern.compile("^LOOP\\s+(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CALL_PATTERN = Pattern.compile("^CALL\\s+(\\w+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_PATTERN = Pattern.compile("^\\s*(#|//).*$");
    private static final Pattern EMPTY_PATTERN = Pattern.compile("^\\s*$");
    private static final Pattern BAUD_COMMAND_PATTERN = Pattern.compile("^BAUD\\s+(\\d+)$", Pattern.CASE_INSENSITIVE);

    // Execution state
    private final int loopCounter = 0;
    private final int loopMax = 0;
    private final boolean inLoop = false;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════╗");
        System.out.println("║      Arduino Script Processor TTL Knight " + VERSION + "      ║");
        System.out.println("║         with Flow Control & Help         ║");
        System.out.println("╚══════════════════════════════════════════╝");

        Main processor = new Main();

        try {
            if (args.length > 0 && (args[0].equals("--help") || args[0].equals("-h"))) {
                processor.showFullHelp();
                return;
            }

            if (args.length > 0) {
                processor.processArgs(args);
            } else {
                processor.interactiveMenu();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            processor.disconnect();
            System.out.println("\nGoodbye!");
        }
    }

    /**
     * Show comprehensive help system
     */
    private void showFullHelp() {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("ARDUINO SCRIPT PROCESSOR - COMPLETE HELP");
        System.out.println("═".repeat(60));

        showQuickReference();
        showDetailedCommands();
        showExamples();
        showCommandLineUsage();
        showBaudRateInfo();
        showTroubleshooting();
    }

    /**
     * Quick reference card
     */
    private void showQuickReference() {
        System.out.println("\n" + "━".repeat(60));
        System.out.println("QUICK REFERENCE");
        System.out.println("━".repeat(60));

        System.out.println("\nMAIN MENU COMMANDS:");
        System.out.println("  1        List serial ports");
        System.out.println("  2        Change baud rate");
        System.out.println("  3        Connect to Arduino");
        System.out.println("  4        Execute script file");
        System.out.println("  5        Direct command mode");
        System.out.println("  6        Show help (this screen)");
        System.out.println("  7        Exit program");

        System.out.println("\nSCRIPT FLOW CONTROL COMMANDS:");
        System.out.println("  10->LABEL        Define label at line 10");
        System.out.println("  GOTO LABEL       Jump to label");
        System.out.println("  WAIT 1000        Wait 1000ms (local)");
        System.out.println("  IF x=1 GOTO Y    Conditional jump");
        System.out.println("  LOOP 5           Start 5-iteration loop");
        System.out.println("  ENDLOOP          End loop block");
        System.out.println("  CALL SUB         Call subroutine");
        System.out.println("  RETURN           Return from subroutine");
        System.out.println("  SET VAR=value    Set variable");
        System.out.println("  BAUD 115200      Change baud rate");
        System.out.println("  ECHO text        Display message");
        System.out.println("  STOP             Stop script execution");

        System.out.println("\nDIRECT MODE COMMANDS (ARDUINO> prompt):");
        System.out.println("  any command      Send to Arduino");
        System.out.println("  baud <rate>      Change baud rate");
        System.out.println("  info             Show connection info");
        System.out.println("  exit             Return to main menu");

        System.out.println("\nCOMMAND MODE COMMANDS (> prompt):");
        System.out.println("  script <file>    Execute script file");
        System.out.println("  baud <rate>      Change baud rate");
        System.out.println("  exit             Return to main menu");
    }

    /**
     * Detailed command explanations
     */
    private void showDetailedCommands() {
        System.out.println("\n" + "━".repeat(60));
        System.out.println("DETAILED COMMAND REFERENCE");
        System.out.println("━".repeat(60));

        System.out.println("\n〚 LABEL DEFINITION 〛");
        System.out.println("  Format: <number>-><label_name>");
        System.out.println("  Example: 10->START");
        System.out.println("  Example: 20->BLINK_ROUTINE");
        System.out.println("  Note: Numbers are manual, not auto-generated");

        System.out.println("\n〚 FLOW CONTROL 〛");
        System.out.println("  GOTO <label>");
        System.out.println("    Jumps to the specified label");
        System.out.println("    Example: GOTO START");

        System.out.println("\n  WAIT <milliseconds>");
        System.out.println("    Pauses execution locally (not sent to Arduino)");
        System.out.println("    Example: WAIT 500    (waits half second)");
        System.out.println("    Example: WAIT 2000   (waits 2 seconds)");

        System.out.println("\n  IF <condition> GOTO <label>");
        System.out.println("    Conditional jump based on simple condition");
        System.out.println("    Conditions: VAR=value, TRUE, FALSE");
        System.out.println("    Example: IF COUNT=5 GOTO FINISH");
        System.out.println("    Example: IF SENSOR=HIGH GOTO ALERT");

        System.out.println("\n  LOOP <count>");
        System.out.println("    Starts a loop that repeats <count> times");
        System.out.println("    Must be paired with ENDLOOP");
        System.out.println("    Example: LOOP 3 ... ENDLOOP");

        System.out.println("\n  CALL <label> / RETURN");
        System.out.println("    Subroutine call and return");
        System.out.println("    Example: CALL BEEP_ROUTINE");
        System.out.println("    Subroutines can be nested");

        System.out.println("\n〚 VARIABLES 〛");
        System.out.println("  SET <var> = <value>");
        System.out.println("    Sets a variable to a value");
        System.out.println("    Example: SET COUNT = 0");
        System.out.println("    Example: SET NAME = \"TEST\"");
        System.out.println("    Use ${VAR} to reference in ECHO commands");

        System.out.println("\n〚 SYSTEM COMMANDS 〛");
        System.out.println("  BAUD <rate>");
        System.out.println("    Changes baud rate for future connections");
        System.out.println("    Example: BAUD 115200");
        System.out.println("    Note: Requires reconnection to take effect");

        System.out.println("\n  ECHO <message>");
        System.out.println("    Displays message in console");
        System.out.println("    Variables: Use ${VAR} in message");
        System.out.println("    Example: ECHO Loop iteration ${COUNT}");

        System.out.println("\n  STOP");
        System.out.println("    Immediately stops script execution");
        System.out.println("    Useful for error conditions");

        System.out.println("\n〚 COMMENTS 〛");
        System.out.println("  # Comment text");
        System.out.println("  // Also comment text");
        System.out.println("  Comments are ignored during execution");
    }

    /**
     * Show practical examples
     */
    private void showExamples() {
        System.out.println("\n" + "━".repeat(60));
        System.out.println("PRACTICAL EXAMPLES");
        System.out.println("━".repeat(60));

        System.out.println("\nExample 1: Simple LED Blink");
        System.out.println("  # LED Blink Test");
        System.out.println("  10->START");
        System.out.println("  LOOP 5");
        System.out.println("  LED ON");
        System.out.println("  WAIT 500");
        System.out.println("  LED OFF");
        System.out.println("  WAIT 500");
        System.out.println("  ENDLOOP");
        System.out.println("  ECHO Blink complete!");

        System.out.println("\nExample 2: Sensor Reading with Variables");
        System.out.println("  # Read sensor 3 times");
        System.out.println("  10->START");
        System.out.println("  SET COUNT = 0");
        System.out.println("  20->READ_LOOP");
        System.out.println("  IF COUNT=3 GOTO FINISH");
        System.out.println("  READ TEMPERATURE");
        System.out.println("  SET COUNT = ${COUNT}+1");
        System.out.println("  WAIT 1000");
        System.out.println("  GOTO READ_LOOP");
        System.out.println("  30->FINISH");
        System.out.println("  ECHO Read ${COUNT} samples");

        System.out.println("\nExample 3: Subroutine with Parameters");
        System.out.println("  # Beep subroutine");
        System.out.println("  10->BEEP");
        System.out.println("  SET TIMES = ${PARAM}");
        System.out.println("  20->BEEP_LOOP");
        System.out.println("  IF TIMES=0 RETURN");
        System.out.println("  BUZZER ON");
        System.out.println("  WAIT 100");
        System.out.println("  BUZZER OFF");
        System.out.println("  WAIT 100");
        System.out.println("  SET TIMES = ${TIMES}-1");
        System.out.println("  GOTO BEEP_LOOP");
        System.out.println("  # Main program");
        System.out.println("  SET PARAM = 3");
        System.out.println("  CALL BEEP");
    }

    /**
     * Show command line usage
     */
    private void showCommandLineUsage() {
        System.out.println("\n" + "━".repeat(60));
        System.out.println("COMMAND LINE USAGE");
        System.out.println("━".repeat(60));

        System.out.println("\nBasic Usage:");
        System.out.println("  java ArduinoScriptProcessor [options] [port] [script]");

        System.out.println("\nCommon Patterns:");
        System.out.println("  # Interactive mode");
        System.out.println("  java ArduinoScriptProcessor");

        System.out.println("\n  # Connect with default baud (9600)");
        System.out.println("  java ArduinoScriptProcessor /dev/ttyUSB0");

        System.out.println("\n  # Connect with specific baud rate");
        System.out.println("  java ArduinoScriptProcessor 115200 /dev/ttyUSB0");

        System.out.println("\n  # Execute script immediately");
        System.out.println("  java ArduinoScriptProcessor /dev/ttyUSB0 myscript.txt");

        System.out.println("\n  # Execute script with specific baud");
        System.out.println("  java ArduinoScriptProcessor 57600 /dev/ttyUSB0 test.txt");

        System.out.println("\nCommand Line Options:");
        System.out.println("  --help, -h       Show this help screen");
        System.out.println("  --list, -l       List available serial ports");
        System.out.println("  --baud <rate>    Specify baud rate");
        System.out.println("  --file <port> <script>  Direct script execution");

        System.out.println("\nExamples:");
        System.out.println("  java ArduinoScriptProcessor --list");
        System.out.println("  java ArduinoScriptProcessor --baud 115200 /dev/ttyUSB0");
        System.out.println("  java ArduinoScriptProcessor --file /dev/ttyUSB0 script.txt");
        System.out.println("  java ArduinoScriptProcessor 19200 /dev/ttyACM0");
    }

    /**
     * Show baud rate information
     */
    private void showBaudRateInfo() {
        System.out.println("\n" + "━".repeat(60));
        System.out.println("BAUD RATE INFORMATION");
        System.out.println("━".repeat(60));

        System.out.println("\nCommon Arduino Baud Rates:");
        System.out.println("  ⭐ 9600      - Default for most Arduino sketches");
        System.out.println("  ⭐ 115200    - Fast serial, common for debugging");
        System.out.println("  ⭐ 57600     - Good balance of speed/reliability");
        System.out.println("  ⭐ 19200     - Older devices, some GPS modules");
        System.out.println("  ⭐ 38400     - MIDI devices, some Bluetooth");

        System.out.println("\nAll Supported Rates:");
        System.out.print("  ");
        for (int i = 0; i < SUPPORTED_BAUD_RATES.length; i++) {
            System.out.printf("%7d", SUPPORTED_BAUD_RATES[i]);
            if ((i + 1) % 5 == 0 && i < SUPPORTED_BAUD_RATES.length - 1) {
                System.out.print("\n  ");
            }
        }
        System.out.println();

        System.out.println("\nImportant:");
        System.out.println("  • Baud rate must match Arduino's Serial.begin()");
        System.out.println("  • Change requires disconnecting/reconnecting");
        System.out.println("  • Use 'BAUD' command in scripts for future connections");
    }

    /**
     * Show troubleshooting tips
     */
    private void showTroubleshooting() {
        System.out.println("\n" + "━".repeat(60));
        System.out.println("TROUBLESHOOTING");
        System.out.println("━".repeat(60));

        System.out.println("\nCommon Issues:");
        System.out.println("1. Port not found:");
        System.out.println("   • Check cable connection");
        System.out.println("   • Run with --list to see available ports");
        System.out.println("   • On Linux: ls /dev/tty*");

        System.out.println("\n2. Permission denied:");
        System.out.println("   • sudo usermod -a -G dialout $USER");
        System.out.println("   • Logout and login again");

        System.out.println("\n3. No response from Arduino:");
        System.out.println("   • Check baud rate matches sketch");
        System.out.println("   • Verify Arduino is powered");
        System.out.println("   • Check Serial Monitor isn't open elsewhere");

        System.out.println("\n4. Script not executing:");
        System.out.println("   • Check file exists and is readable");
        System.out.println("   • Verify script syntax (labels, commands)");
        System.out.println("   • Try dry run first (option 3 in menu)");

        System.out.println("\nGetting Help:");
        System.out.println("  • Use --help for this reference");
        System.out.println("  • Check script examples above");
        System.out.println("  • Ensure Arduino sketch uses correct baud rate");
    }

    /**
     * Show brief help in interactive mode
     */
    private void showBriefHelp() {
        System.out.println("\n" + "═".repeat(50));
        System.out.println("ARDUINO SCRIPT PROCESSOR - QUICK HELP");
        System.out.println("═".repeat(50));

        System.out.println("\nKey Features:");
        System.out.println("  • Scripting with labels and flow control");
        System.out.println("  • Baud rate selection (300-1000000)");
        System.out.println("  • Variables and conditional execution");
        System.out.println("  • Subroutines and loops");

        System.out.println("\nQuick Start:");
        System.out.println("  1. Choose 'List serial ports' to find Arduino");
        System.out.println("  2. Select baud rate (usually 9600 or 115200)");
        System.out.println("  3. Connect to Arduino");
        System.out.println("  4. Run a script or use direct commands");

        System.out.println("\nFor full help: Choose option 6 or run with --help");
        System.out.println("For examples: See the examples section in full help");
    }

    /**
     * Interactive menu with help option
     */
    private void interactiveMenu() {
        while (true) {
            System.out.println("\n" + "━".repeat(50));
            System.out.println("MAIN MENU (Baud: " + currentBaudRate + ")");
            System.out.println("━".repeat(50));
            System.out.println("1. List serial ports");
            System.out.println("2. Change baud rate");
            System.out.println("3. Connect to Arduino");
            System.out.println("4. Execute script file");
            System.out.println("5. Direct command mode");
            System.out.println("6. Show help");
            System.out.println("7. Exit");
            System.out.print("\nChoice (1-7): ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    listPorts();
                    break;
                case "2":
                    changeBaudRate();
                    break;
                case "3":
                    connectMenu();
                    break;
                case "4":
                    scriptMenu();
                    break;
                case "5":
                    if (connected) {
                        directCommandMode();
                    } else {
                        System.out.println("Not connected. Connect first (option 3).");
                    }
                    break;
                case "6":
                    showBriefHelp();
                    System.out.print("\nShow full help? (y/N): ");
                    String showFull = scanner.nextLine().trim();
                    if (showFull.equalsIgnoreCase("y")) {
                        showFullHelp();
                    }
                    break;
                case "7":
                    return;
                default:
                    System.out.println("Invalid choice. Enter 1-7 or 'help'.");
                    if (choice.equalsIgnoreCase("help")) {
                        showBriefHelp();
                    }
            }
        }
    }

    /**
     * Connect menu with help tip
     */
    private void connectMenu() {
        if (connected) {
            System.out.println("Already connected. Disconnect first.");
            return;
        }

        System.out.println("\n" + "━".repeat(40));
        System.out.println("CONNECT TO ARDUINO");
        System.out.println("━".repeat(40));
        System.out.println("Current baud rate: " + currentBaudRate);
        System.out.println("Tip: Use option 1 to list available ports");
        System.out.print("\nEnter port (e.g., /dev/ttyUSB0): ");
        String port = scanner.nextLine().trim();

        if (port.isEmpty()) {
            System.out.println("Cancelled.");
            return;
        }

        System.out.print("Use " + currentBaudRate + " baud? (Y/n): ");
        String useCurrent = scanner.nextLine().trim();

        if (useCurrent.equalsIgnoreCase("n")) {
            changeBaudRate();
        }

        connect(port);
    }

    /**
     * Script menu with help
     */
    private void scriptMenu() {
        if (!connected) {
            System.out.println("Not connected. Connect first (option 3).");
            return;
        }

        System.out.println("\n" + "━".repeat(40));
        System.out.println("EXECUTE SCRIPT FILE");
        System.out.println("━".repeat(40));
        System.out.println("Current baud rate: " + currentBaudRate);
        System.out.println("Script commands: GOTO, WAIT, IF, LOOP, CALL, etc.");
        System.out.println("See help (option 6) for full command reference");
        System.out.print("\nEnter script filename: ");
        String filename = scanner.nextLine().trim();

        if (filename.isEmpty()) {
            return;
        }

        System.out.println("\nExecution modes:");
        System.out.println("  1. Normal execution");
        System.out.println("  2. Step-by-step (debug)");
        System.out.println("  3. Dry run (parse only)");
        System.out.print("\nChoice (1-3): ");

        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1":
                executeScriptFile(filename);
                break;
            case "2":
                executeStepByStep(filename);
                break;
            case "3":
                try {
                    parseScriptFile(filename, true);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            default:
                System.out.println("Invalid choice");
        }
    }

    /**
     * Direct command mode with help reminder
     */
    private void directCommandMode() {
        System.out.println("\n" + "═".repeat(50));
        System.out.println("DIRECT COMMAND MODE");
        System.out.println("═".repeat(50));
        System.out.println("Baud rate: " + currentBaudRate);
        System.out.println("Type commands to send directly to Arduino");
        System.out.println("\nLocal commands:");
        System.out.println("  baud <rate>    - Change baud rate");
        System.out.println("  info           - Show connection info");
        System.out.println("  help           - Show brief help");
        System.out.println("  exit           - Return to main menu");
        System.out.println("\nAll other input sent to Arduino");
        System.out.println("═".repeat(50) + "\n");

        startSerialReader();

        while (true) {
            System.out.print("ARDUINO> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                break;
            } else if (input.equalsIgnoreCase("info")) {
                showConnectionInfo();
            } else if (input.equalsIgnoreCase("help")) {
                System.out.println("\nDirect Mode Help:");
                System.out.println("  • Type any command to send to Arduino");
                System.out.println("  • Use 'baud <rate>' to change baud rate");
                System.out.println("  • Use 'info' to see connection details");
                System.out.println("  • Use 'exit' to return to main menu");
                System.out.println("  • Arduino should echo responses");
            } else if (input.toLowerCase().startsWith("baud ")) {
                handleBaudCommand(input);
            } else if (!input.isEmpty()) {
                sendToArduino(input);
            }
        }
    }

    /**
     * Handle baud rate change command
     */
    private void handleBaudCommand(String input) {
        try {
            String[] parts = input.split(" ");
            int newBaud = Integer.parseInt(parts[1]);
            if (isValidBaudRate(newBaud)) {
                currentBaudRate = newBaud;
                System.out.println("✓ Baud rate changed to: " + currentBaudRate);
                if (connected) {
                    System.out.println("Note: Disconnect and reconnect to apply new rate.");
                }
            } else {
                System.out.println("✗ Unsupported baud rate.");
                System.out.println("Supported: " + Arrays.toString(SUPPORTED_BAUD_RATES));
            }
        } catch (Exception e) {
            System.out.println("Usage: baud <rate>");
            System.out.println("Example: baud 115200");
        }
    }

    /**
     * Show connection information
     */
    private void showConnectionInfo() {
        if (!connected) {
            System.out.println("Not connected.");
            return;
        }

        try {
            String portName = (String) serialPort.getClass()
                    .getMethod("getSystemPortName").invoke(serialPort);
            String description = (String) serialPort.getClass()
                    .getMethod("getPortDescription").invoke(serialPort);

            System.out.println("\n" + "━".repeat(40));
            System.out.println("CONNECTION INFORMATION");
            System.out.println("━".repeat(40));
            System.out.println("Port:        " + portName);
            System.out.println("Description: " + description);
            System.out.println("Baud rate:   " + currentBaudRate);
            System.out.println("Data bits:   8");
            System.out.println("Stop bits:   1");
            System.out.println("Parity:      None");
            System.out.println("Status:      Connected");
            System.out.println("━".repeat(40));

        } catch (Exception e) {
            System.out.println("Could not get connection info.");
        }
    }

    // ========== REST OF THE IMPLEMENTATION ==========
    // (Previous methods remain unchanged, just integrating the help system)

    private void processArgs(String[] args) {
        // ... (same as before)
    }

    private void changeBaudRate() {
        // ... (same as before)
    }

    private void showSupportedBaudRates() {
        // ... (same as before)
    }

    private boolean isValidBaudRate(int baudRate) {
        // ... (same as before)
        return false;
    }

    private void connect(String portName) {
        // ... (same as before)
    }

    private void testConnection() {
        // ... (same as before)
    }

    private int executeLine(List<ScriptLine> script, ScriptLine line, int currentIndex) {
        // ... (same as before)
        return 0;
    }

    private void executeScriptFile(String filename) {
        // ... (same as before)
    }

    private List<ScriptLine> parseScriptFile(String filename, boolean dryRun) throws IOException {
        // ... (same as before)
        return null;
    }

    private void executeScript(List<ScriptLine> script, int start, int end) {
        // ... (same as before)
    }

    private void executeStepByStep(String filename) {
        // ... (same as before)
    }

    private boolean evaluateCondition() {
        // ... (same as before)
        return true;
    }

    private void sendToArduino(String command) {
        if (!connected) {
            System.out.println("  ✗ Not connected to Arduino");
            return;
        }

        try {
            String toSend = command.trim();
            if (!toSend.endsWith("\r") && !toSend.endsWith("\n")) {
                toSend += "\r\n";
            }

            System.out.println("  → Arduino: " + command);
            serialOut.write(toSend.getBytes());
            serialOut.flush();

        } catch (IOException e) {
            System.out.println("  ✗ Send failed: " + e.getMessage());
            disconnect();
        }
    }

    private void sendRaw(String data) throws IOException {
        // ... (same as before)
    }

    private void startSerialReader() {
        // ... (same as before)
    }

    private void listPorts() {
        // ... (same as before)
    }

    private void disconnect() {
        // ... (same as before)
    }

    private void cleanup() {
        // ... (same as before)
    }

    private boolean isComment(String line) {
        // ... (same as before)
        return false;
    }

    private boolean isEmpty(String line) {
        // ... (same as before)
        return false;
    }

    private void resetExecutionState() {
        // ... (same as before)
    }

    private void commandLoop() {
        System.out.println("\n" + "═".repeat(50));
        System.out.println("COMMAND MODE");
        System.out.println("═".repeat(50));
        System.out.println("Baud rate: " + currentBaudRate);
        System.out.println("Type 'script <file>' to execute a script");
        System.out.println("Type 'baud <rate>' to change baud rate");
        System.out.println("Type 'help' for brief help");
        System.out.println("Type 'exit' to return to main menu");
        System.out.println("═".repeat(50) + "\n");

        startSerialReader();

        while (true) {
            System.out.print("> ");
            String input = scanner.nextLine().trim();

            if (input.equalsIgnoreCase("exit")) {
                break;
            } else if (input.equalsIgnoreCase("help")) {
                System.out.println("\nCommand Mode Help:");
                System.out.println("  script <file>  - Execute script file");
                System.out.println("  baud <rate>    - Change baud rate");
                System.out.println("  exit           - Return to main menu");
                System.out.println("  <any command>  - Send to Arduino");
            } else if (input.startsWith("script ")) {
                String filename = input.substring(7).trim();
                executeScriptFile(filename);
            } else if (input.toLowerCase().startsWith("baud ")) {
                handleBaudCommand(input);
            } else if (!input.isEmpty()) {
                sendToArduino(input);
            }
        }
    }

    private static class ScriptLine {
        // ... (same as before)
        final String original;
        final String command;
        final int lineNumber;

        ScriptLine(String original, String command, int lineNumber) {
            this.original = original;
            this.command = command;
            this.lineNumber = lineNumber;
        }
    }
}