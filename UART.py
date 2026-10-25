from operator import pos

import serial
import time
import sys
import struct
import traceback
# Definitions
# serial_port = '/dev/ttyUSB1'
serial_port = 'COM3'  # Change this to your actual serial port
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

def validate_move(board, from_pos, to_pos):
    piece = board[from_pos]
    # Only for pawns and kings for now
    if piece == 0x0001:  # White Pawn
        if to_pos == from_pos - 8 and chessPieces.get(board[to_pos], "?") == "0":  # Move forward
            return True
        elif to_pos == from_pos - 16 and from_pos // 8 == 6 and chessPieces.get(board[to_pos], "?") == "0":  # Initial double move
            return True
        elif to_pos == from_pos - 7 and from_pos % 8 != 7 and chessPieces.get(board[to_pos], "?") != "0":  # Capture right
            return True
        elif to_pos == from_pos - 9 and from_pos % 8 != 0 and chessPieces.get(board[to_pos], "?") != "0":  # Capture left
            return True
        else:
            return False
    elif piece == 0x0006:  # White King
        if abs(to_pos - from_pos) in [1, 8]:  # Move one square in any direction
            return True
        elif abs(to_pos - from_pos) == 9 and (from_pos % 8 != 0):  # Move diagonally left
            return True
        elif abs(to_pos - from_pos) == 7 and (from_pos % 8 != 7):  # Move diagonally right
            return True
        else:
            return False
    else:
        return False  # For now only pawns and kings are valid

def send_data(ser, board): # TODO: Implement this function to send chess board via UART
    frame = board_to_bytearray(board)
    ser.write(frame)
    print("Sent chess board data:")
    print(board_to_string(board))

def receive_data(ser): # Listen to UART data, that descripes our 8x8 chess board with each square being two bytes
    buffer = bytearray()
    BOARD_BYTES = int( 8 * 8 * 0.5)  # 8x8 board, each square is half a byte
    try:
        print("Listening for chess board data...")
        while True: # Wait for the full board data to be received
            chunk = ser.read(max(1, ser.in_waiting))

            if chunk:
                buffer.extend(chunk)
            while len(buffer) >= BOARD_BYTES: # If we have enough data for a full board, process it
                frame = buffer[:BOARD_BYTES] # Extract the frame from the buffer
                buffer = buffer[BOARD_BYTES:] # Remove the processed frame from the buffer

                return bytearray_to_board(frame)
                    
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
            updatedBoard = False
            while True: #loop through game states
                if not updatedBoard:
                    board = receive_data(ser)
                    updatedBoard = True
                print("Received chess board data:")
                print(board_to_string(board))
                print("Select an chess square (example: a1):")
                pos = input().strip().lower()
                if len(pos) != 2 or pos[0] not in 'abcdefgh' or pos[1] not in '12345678':
                    print("Invalid input. Please enter a valid chess square (e.g., a1, b2, etc.).")
                    continue # Break or exit(0) ?

                col = ord(pos[0].lower()) - ord('a')
                row = 8 - int(pos[1])  # Convert to 0-indexed
                index = row * 8 + col
                piece = board[index]
                if not check_if_piece_exists(piece):
                    print(f"No piece exists at {pos}. Please select a square with a piece.")
                    continue
            
                print(f"Selected piece: {chessPieces.get(piece, '?')} at {pos} (Hex: {piece:#04x})")
                print(f"Piece at {pos}")
                print("Enter the new position for the piece:")
                new_pos = input().strip().lower()
                if len(new_pos) != 2 or new_pos[0] not in 'abcdefgh' or new_pos[1] not in '12345678':
                    print("Invalid input. Please enter a valid chess square (e.g., a1, b2, etc.).")
                    continue
                new_col = ord(new_pos[0].lower()) - ord('a')
                new_row = 8 - int(new_pos[1])  # Convert to 0
                new_index = new_row * 8 + new_col
                if not validate_move(board, index, new_index):
                    print(f"Invalid move from {pos} to {new_pos}. Please try again.")
                    continue
                # Update the board with the new position
                board[new_index] = piece
                board[index] = 0x0000  # Clear the original position
                updatedBoard = False  # Reset to receive the updated board from UART
                send_data(ser, board)

    


    except Exception:
        traceback.print_exc()
        print("Couldnt open serial port.")

if __name__ == "__main__":
    main()