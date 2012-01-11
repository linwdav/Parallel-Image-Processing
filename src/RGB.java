/**
 * Convert between RGB and int.
 * @author David Lin
 */
public class RGB {

  /**
   * Convert RGB to int.
   * @param bytes Array of RGB values
   * @return Value of RGB as an int
   */
  public static int bytesToInt(byte bytes[]) {
    int value = 0;
    for (int i = 0; i < bytes.length; i++) {
      int shift = (bytes.length - 1 - i) * 8;
      value += (bytes[i] & 0x000000FF) << shift;
    }
    return value;
  }

  /**
   * Convert int to RGB.
   * @param rgb RGB value as an int
   * @return Array of RGB values
   */
  public static byte[] intToBytes(int rgb) {
    byte[] bytes = new byte[3];
    for (int i = 0; i < 3; i++) {
      int offset = (2 - i) * 8;
      bytes[i] = (byte) ((rgb >>> offset) & 0xFF);
    }
    return bytes;
  }

}
