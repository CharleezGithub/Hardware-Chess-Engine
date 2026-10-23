import serial
import time
import sys
import struct

# Definitions
serial_port = '/dev/ttyUSB0'
baud_rate = 115200

def send_data(ser, data): # TODO: Implement this function to send chess board via UART
    return

def receive_data(ser): # Listen to UART data, that descripes our 8x8 chess board with each square being two bytes
    buffer = bytearray()
    try:
        output = bytearray()
        while True:
            if ser.in_waiting > 0:
                data = ser.read(ser.in_waiting)
                buffer.extend(data)
                while len(buffer) >= 2:
                    square_data = buffer[:2]
                    buffer = buffer[2:]
                    square_value = struct.unpack('<H', square_data)[0]  # Unpack as little-endian unsigned short
                    output.append(square_value)

            print("Received chess board data:")
            for i in range(8):
                row = output[i*8:(i+1)*8]
                print(' '.join(f"{val:04x}" for val in row))
            time.sleep(1)  # Adjust the sleep time as needed
                    
    except KeyboardInterrupt:
        print("KeyboardInterrupt detected. Exiting...")
        return
    
def main():
    print("Current settings:")
    print(f"Serial port: {serial_port}")
    print(f"Baud rate: {baud_rate}")

    print("Press enter to continue or e to exit.")
    
    if input() == 'e':
        return

    try:
        ser = serial.Serial(serial_port, baud_rate, timeout=1)
        receive_data(ser)


    except Exception:
        print("Couldnt open serial port.")