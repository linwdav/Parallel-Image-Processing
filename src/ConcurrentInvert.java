import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Vector;
import javax.imageio.ImageIO;

/**
 * Invert an image using parallel processing.
 * @author David Lin
 */
public class ConcurrentInvert {

  private String inputFileName;
  private String outputFileName;
  private BufferedImage inputImage;
  private BufferedImage outputImage;

  /* Maximum size of 8-bit pixel. */
  private static final byte MAX_BYTE = (byte) 255;

  /**
   * Constructor.
   * @param inputFileName Name of input image file
   * @param outputFileName Name of output image file
   */
  public ConcurrentInvert(String inputFileName, String outputFileName) {
    this.inputFileName = inputFileName;
    this.outputFileName = outputFileName;
  }

  /**
   * Load image into memory.
   * @throws IllegalArgumentException Illegal argument
   * @throws IOException IO exception
   */
  public void load() throws IllegalArgumentException, IOException {
    inputImage = ImageIO.read(new File(inputFileName));
  }

  /**
   * Process and invert the image.
   * @param numThreads Number of parallel threads to process image.
   */
  private void process(int numThreads) {

    // Output image settings
    int width = inputImage.getWidth();
    int height = inputImage.getHeight();
    outputImage = new BufferedImage(width, height, inputImage.getType());

    // Array to store the number of pixels each thread is responsible for.
    int[] numComputation = new int[numThreads];

    // Determine how many pixels each thread will process, then store into array.
    int totalPixels = width * height;
    for (int i = 0; i < numThreads; i++) {
      numComputation[i] = totalPixels / numThreads;
    }

    // Split up the remaining pixels as evenly as possible.
    int remainder = totalPixels % numThreads;
    for (int i = 0; i < remainder; i++) {
      numComputation[i] = numComputation[i] + 1;
    }

    // Container for storing each thread's start and end pixel
    ThreadInfo[] threadinfo = new ThreadInfo[numThreads];
    for (int i = 0; i < numThreads; i++) {
      threadinfo[i] = new ThreadInfo();
    }

    // Distribute work across threads by finding their start and end pixels.
    int index = -1;
    int count = 0;
    for (int i = 0; i < height; i++) {
      for (int j = 0; j < width; j++) {
        if (count == 0) {
          // Start, inclusive
          index++;
          count = numComputation[index];
          threadinfo[index].xStart = j;
          threadinfo[index].yStart = i;
        }
        count--;
        if (count == 0) {
          // End, inclusive
          threadinfo[index].xEnd = j;
          threadinfo[index].yEnd = i;
        }
      }
    }

    // Create threads
    Vector<WorkerThread> threadTable = new Vector<WorkerThread>(0);
    for (int i = 0; i < numThreads; i++) {
      WorkerThread worker = new WorkerThread(threadinfo[i], width, height);
      threadTable.add(worker);
    }

    // Start threads
    for (int i = 0; i < numThreads; i++) {
      threadTable.get(i).start();
    }
    // Wait for every thread to return
    for (int i = 0; i < numThreads; i++) {
      try {
        threadTable.get(i).join();
      }
      catch (InterruptedException e) {
        System.out.println("Thread interrupted");
      }
    }

    // Determine which thread took the longest time to process and print it out
    long maxTime = threadTable.get(0).time;
    for (int i = 1; i < numThreads; i++) {
      long time = threadTable.get(i).time;
      if (time > maxTime) {
        maxTime = time;
      }
    }
    System.out.format("Processing time: %dms\n", maxTime);

  }

  /**
   * Thread to process a set of pixels.
   */
  public class WorkerThread extends Thread {

    private ThreadInfo threadinfo;
    private int width;
    private int height;
    private int time;

    /**
     * Worker thread constructor.
     * @param threadinfo Contains the start and end pixel to process
     * @param width Image width
     * @param height Image height
     */
    public WorkerThread(ThreadInfo threadinfo, int width, int height) {
      this.threadinfo = threadinfo;
      this.width = width;
      this.height = height;
      this.time = 0;
    }

    /**
     * Run thread.
     */
    public void run() {
      // Set to correct pixel in the image
      int i = threadinfo.yStart;
      int j = threadinfo.xStart;
      long start;

      // Begin processing
      for (; i < height; i++) {
        for (; j < width; j++) {
          start = System.currentTimeMillis();
          // Record time it takes to process each pixel
          int pixelRGB = processPixel(inputImage, j, i);
          time += System.currentTimeMillis() - start;
          outputImage.setRGB(j, i, pixelRGB);
          if (j == threadinfo.xEnd && i == threadinfo.yEnd) {
            return;
          }
        }
        j = 0;
      }
    }

  }

  /**
   * Process a pixel's RGB value.
   * @param image The image to process
   * @param x The x coordinate of the pixel to process
   * @param y The y coordinate of the pixel to process
   * @return The pixel's integer value which represents its color
   */
  private int processPixel(BufferedImage image, int x, int y) {

    // Get RGB value and convert to bytes
    int rgb = image.getRGB(x, y);
    byte[] bytes = RGB.intToBytes(rgb);

    // Invert each RGB value by using the formula: 255 - original
    bytes[0] = (byte) ((MAX_BYTE & 0xFF) - bytes[0]);
    bytes[1] = (byte) ((MAX_BYTE & 0xFF) - bytes[1]);
    bytes[2] = (byte) ((MAX_BYTE & 0xFF) - bytes[2]);

    return RGB.bytesToInt(bytes);
  }

  /**
   * Write image.
   * @throws IOException IO exception
   */
  private void save() throws IOException {
    ImageIO.write(outputImage, "jpg", new File(outputFileName));
  }

  /**
   * Main execution.
   * @param args Takes two arguments, the input image file, and the number of threads
   */
  public static void main(String args[]) {

    // Check for two valid arguments
    if (args.length < 2) {
      System.err.println("Usage: java ConcurrentInvert <input file> <# threads>");
      System.exit(1);
    }

    ConcurrentInvert imageInverter = new ConcurrentInvert(args[0], "inverted.jpg");

    // Get number of threads to run
    int numThreads;
    try {
      numThreads = Integer.parseInt(args[1]);
      if (numThreads < 1) {
        System.err.println("Invalid # threads");
        System.exit(1);
      }
      imageInverter.load();
      imageInverter.process(numThreads);
      imageInverter.save();
    }
    catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    catch (NumberFormatException e) {
      System.err.println("Invalid # threads");
      System.err.println("Usage: java ConcurrentInvert <input file> <# threads>");
      System.exit(1);
    }
  }

}
