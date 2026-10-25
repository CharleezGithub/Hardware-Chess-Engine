import serial
import time
import sys
import struct

# Definitions
serial_port = '/dev/ttyUSB1'
baud_rate = 115200

chessPieces = {
    0x0000: "0", # Empty square
    0x0001: "P", # White Pawn
    0x0002: "B", # White Bishop
    0x0003: "N", # White Knight
    0x0004: "R", # White Rook
    0x0005: "Q", # White Queen
    0x0006: "K", # White King
    0x0007: "p", # Black Pawn
    0x0008: "b", # Black Bishop
    0x0009: "n", # Black Knight
    0x000A: "r", # Black Rook
    0x000B: "q", # Black Queen
    0x000C: "k", # Black King
}

def board_to_string(squares):
    output = ""
    for i, square_val in enumerate(squares):
        pieceString = chessPieces.get(square_val, "?")
        output += pieceString + " "
        if (i + 1) % 8 == 0:
            output += "\n"
    return output


def send_data(ser, data): # TODO: Implement this function to send chess board via UART
    return

def receive_data(ser): # Listen to UART data, that descripes our 8x8 chess board with each square being two bytes
    buffer = bytearray()
    BOARD_BYTES = 8 * 8 * 2  # 8x8 board, each square is 2 bytes
    try:
        print("Listening for chess board data...")
        while True:
            chunk = ser.read(max(1, ser.in_waiting))

            if chunk:
                buffer.extend(chunk)
            while len(buffer) >= BOARD_BYTES:
                frame = buffer[:BOARD_BYTES]
                buffer = buffer[BOARD_BYTES:]

                squares = struct.unpack('>64H', frame)  # Unpack 64 unsigned shorts (16-bit)

                print("Received chess board data:")
                print(board_to_string(squares))
                    
    except KeyboardInterrupt:
        print("KeyboardInterrupt detected. Exiting...")
        return
    
def main():
    print("Current settings:")
    print(f"Serial port: {serial_port}")
    print(f"Baud rate: {baud_rate}")

    print("Press enter to continue or e to exit.")
    
    if input().strip().lower() == 'e':
        return

    try:
        with serial.Serial(
            port=serial_port,
            baudrate=baud_rate,
            bytesize=serial.EIGHTBITS,
            parity=serial.PARITY_NONE,
            stopbits=serial.STOPBITS_ONE,
            timeout=0.1
        ) as ser:
            receive_data(ser)


    except Exception:
        print("Couldnt open serial port.")

if __name__ == "__main__":
    main()