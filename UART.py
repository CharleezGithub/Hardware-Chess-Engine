from operator import pos

import serial
import time
import sys
import struct
import traceback
# Definitions
serial_port = '/dev/ttyUSB1'
# serial_port = 'COM3'  # Change this to your actual serial port
baud_rate = 115200
initial_board = [
    "0", "0", "0", "0", "k", "0", "0", "0",
    "p", "p", "p", "p", "p", "p", "p", "p",
    "0", "0", "0", "0", "0", "0", "0", "0",
    "0", "0", "0", "0", "0", "0", "0", "0",
    "0", "0", "0", "0", "0", "0", "0", "0",
    "0", "0", "0", "0", "0", "0", "0", "0",
    "P", "P", "P", "P", "P", "P", "P", "P",
    "0", "0", "0", "0", "K", "0", "0", "0"
]

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

pieceValues = {
    "0": 0x00, # Empty square
    "P": 0x01, "B": 0x02, "N": 0x03, "R": 0x04, "Q": 0x05, "K": 0x06,
    "p": 0x07, "b": 0x08, "n": 0x09, "r": 0x0A, "q": 0x0B, "k": 0x0C
}

STATUS_MESSAGES = {
    0: "White to move",
    1: "Black to move",
    2: "White wins (Checkmate)!",
    3: "Black wins (Checkmate)!",
    4: "Draw / Stalemate",
    5: "Illegal move attempted!",
    6: "Bad command / Checksum error from FPGA",
}

# Do note, the initial state of the board is completly dependent on the hardware atm
board = [0x0000] * 64 # Full empty board initialization

def check_if_piece_exists(piece: str ) -> bool:
    if chessPieces.get(piece, "?") == "?" or chessPieces.get(piece, "?") == "0":
        return False
    return True

def board_to_string(squares):
    output = "8: " # First row label
    for i, square_val in enumerate(squares):
        pieceString = chessPieces.get(square_val, "?")
        output += pieceString + " "
        if (i + 1) % 8 == 0 and (i + 1) != len(squares):
            output += f"\n{8 - ((i + 1) // 8) }: "
    return output + "\n   a b c d e f g h"

def bytearray_to_board(byteArr):
    squares = []
    for byte in byteArr:
        squares.append((byte >> 4))  # High nibble
        squares.append(byte & 0x0F)         # Low nibble
    return squares

def board_to_bytearray(squares):
    byteArr = bytearray()
    for i in range(0, len(squares), 2):
        high_nibble = squares[i] & 0x0F
        low_nibble = squares[i + 1] & 0x0F
        byteArr.append((high_nibble << 4) | low_nibble)
    return byteArr

def send_command(ser, cmd=0x03, from_pos=0, to_pos=0):
    """
    Sends the 5-byte packet expected by UartTop:
    [0xFF, COMMAND, FROM, TO, CHECKSUM]
    cmd: 0x01 = Reset game, 0x02 = Play move
    """
    checksum = (cmd ^ from_pos ^ to_pos) & 0xFF
    packet = bytes([0xFF, cmd, from_pos, to_pos, checksum])
    ser.write(packet)
    ser.flush()

def receive_data(ser): # Listen to UART data, that descripes our 8x8 chess board with each square being two bytes
    buffer = bytearray()
    BOARD_BYTES = int( 8 * 8 * 0.5) + 1  # 8x8 board, each square is half a byte + status
    print("Listening for chess board data...")
    while True: # Wait for the full board data to be received
        chunk = ser.read(max(1, ser.in_waiting))

        if chunk:
            buffer.extend(chunk)
        while len(buffer) >= BOARD_BYTES: # If we have enough data for a full board, process it
            frame = buffer[:BOARD_BYTES] # Extract the frame from the buffer
            del buffer[:BOARD_BYTES] # Remove the processed frame from the buffer

            board = bytearray_to_board(frame[:32])
            status = frame[32]
            
            return board, status

def set_square(ser, square_idx, piece_code):
    # Sends CMD=4 to set a specific square on the board
    send_command(ser, cmd=0x04, from_pos=square_idx, to_pos=piece_code)
    receive_data(ser) # Wait for acknowledgment from FPGA


def load_custom_board(ser, board):
    print("loading custom board...")
    for idx, piece in enumerate(board):
        set_square(ser, idx, piece)
    print("Custom board loaded successfully.")    

def string_to_board(board_str):
    board = []
    for piece in board_str:
        piece_code = pieceValues.get(piece, 0x00)  # Default to empty square if not found
        board.append(piece_code)
    return board

def parse_square(square):
    sq = square.strip().lower()
    if len(sq) != 2 or sq[0] not in 'abcdefgh' or sq[1] not in '12345678':
        return None
    col = ord(sq[0]) - ord('a')
    row = 8 - int(sq[1])  # Convert to 0-index
    return row * 8 + col

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
            timeout=1
        ) as ser:
            ser.reset_input_buffer()  # Clear any existing data in the input buffer
            ser.reset_output_buffer() # Clear any existing data in the output buffer

            send_command(ser, cmd=0x01)  # Send reset command to FPGA
            board = string_to_board(initial_board)  # Initialize the board with the initial state
            load_custom_board(ser, board)  # Load the initial board state into the FPGA

            status = 0  # Initialize status to 0 (White to move)

            while True: #loop through game states

                status_desc = STATUS_MESSAGES.get(status, f"Unknown status ({status})")
 
                print("\n" + "=" * 25)
                print(f"Engine Status: {status_desc}")
                print("=" * 25)
                print(board_to_string(board))
                if status in [2, 3, 4]:  # Game over statuses
                    print("game over.")
                    break

                from_pos = None
                while from_pos is None:
                    src = input("\nSelect piece to move (e.g. e2) or 'r' to reset: ").strip()
                    if src.lower() == 'r':
                        send_command(ser, cmd=0x01)
                        continue
                    from_pos = parse_square(src)
                    if from_pos is None:
                        print("Invalid square format. Use format like 'e2'.")
                    elif board[from_pos] == 0:
                        print(f"Square {src} is empty!")
                        from_pos = None

                to_pos = None
                while to_pos is None:
                    dst = input("Enter destination square (e.g. e4): ").strip()
                    to_pos = parse_square(dst)
                    if to_pos is None:
                        print("Invalid square format. Use format like 'e4'.")

                print(f"Sending move from {src} to {dst}...")
                send_command(ser, cmd=0x03, from_pos=from_pos, to_pos=to_pos)
                board, status = receive_data(ser)  # Receive the board and status from FPGA

    except KeyboardInterrupt:
        print("KeyboardInterrupt detected. Exiting...")
    except serial.SerialException as e:
        print(f"SerialException: {e}")
    except Exception:
        traceback.print_exc()
        print("Couldnt open serial port.")

if __name__ == "__main__":
    main()