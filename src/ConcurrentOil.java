import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import javax.imageio.ImageIO;

/**
 * Oil an image using parallel processing.
 * @author David Lin
 */
public class ConcurrentOil {

  private String inputFileName;
  private String outputFileName;
  private BufferedImage inputImage;
  private BufferedImage outputImage;

  /**
   * Constructor.
   * @param inputFileName Name of input image file
   * @param outputFileName Name of output image file
   */
  public ConcurrentOil(String inputFileName, String outputFileName) {
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
   * Process and oil the image.
   * @param radius Number of surrounding pixels to use in oil computation.
   * @param numThreads Number of parallel threads to process image.
   */
  private void process(int radius, int numThreads) {

    int width = inputImage.getWidth();
    int height = inputImage.getHeight();
    outputImage = new BufferedImage(width, height, inputImage.getType());

    int[] numComputation = new int[numThreads];

    // Determine how many pixels each thread will process, then store into array.
    int totalPixels = width * height;
    for (int i = 0; i < numThreads; i++) {
      numComputation[i] = totalPixels / numThreads;
    }
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
      WorkerThread worker = new WorkerThread(threadinfo[i], width, height, radius);
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
    private int radius;
    private int time;

    /**
     * Worker thread constructor.
     * @param threadinfo Contains the start and end pixel to process
     * @param width Image width
     * @param height Image height
     * @param radius Number of surrounding pixels to use in oil computation
     */
    public WorkerThread(ThreadInfo threadinfo, int width, int height, int radius) {
      this.threadinfo = threadinfo;
      this.width = width;
      this.height = height;
      this.radius = radius;
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
          int pixelRGB = processPixel(inputImage, j, i, radius);
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
   * @param radius Number of surrounding pixels to use in oil computation
   * @return The pixel's integer value which represents its color
   */
  private int processPixel(BufferedImage image, int x, int y, int radius) {

    int rgb = image.getRGB(x, y);
    byte[] bytes = RGB.intToBytes(rgb);
    bytes[0] = getMostCommonValue(image, 0, x, y, radius);
    bytes[1] = getMostCommonValue(image, 1, x, y, radius);
    bytes[2] = getMostCommonValue(image, 2, x, y, radius);

    return RGB.bytesToInt(bytes);
  }

  /**
   * Determine the most common RGB value amongst pixels within radius of center pixel.
   * @param image The image to process
   * @param rgbColor 0=R, 1=G, 2=B
   * @param x The x coordinate of the pixel to process
   * @param y The y coordinate of the pixel to process
   * @param radius Number of surrounding pixels to use in oil computation
   * @return The most common RGB value represented as a byte
   */
  private byte getMostCommonValue(BufferedImage image, int rgbColor, int x, int y, int radius) {
    Map<Integer, Integer> hashMap = new HashMap<Integer, Integer>();
    // Loop through pixels in given radius and store RGB occurrences in hashmap
    for (int i = y + radius; i >= y - radius; i--) {
      for (int j = x + radius; j >= x - radius; j--) {
        // Must be within bounds
        if (j > -1 && j < image.getWidth() && i > -1 && i < image.getHeight()) {
          int rgb = image.getRGB(j, i);
          byte[] bytes = RGB.intToBytes(rgb);
          if (hashMap.containsKey((bytes[rgbColor] & 0xFF))) {
            // If key is already in hashmap, increment value
            hashMap.put((bytes[rgbColor] & 0xFF), hashMap.get((bytes[rgbColor] & 0xFF)) + 1);
          }
          else {
            // Otherwise add new key
            hashMap.put((bytes[rgbColor] & 0xFF), 1);
          }
        }
      }
    }

    // Determine which RGB value occurs the most often
    Map.Entry<Integer, Integer> maxEntry = null;
    for (Map.Entry<Integer, Integer> entry : hashMap.entrySet()) {
      if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
        maxEntry = entry;
      }
    }

    // Return RGB value
    return maxEntry.getKey().byteValue();

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
   * @param args Takes three arguments, the input image file, radius, and the number of threads
   */
  public static void main(String args[]) {

    // Check for three valid arguments
    if (args.length < 3) {
      System.err.println("Usage: java ConcurrentOil <input file> <radius> <# threads>");
      System.exit(1);
    }

    ConcurrentOil imageOiler = new ConcurrentOil(args[0], "oiled.jpg");

    int radius;
    int numThreads;

    // Get number of threads to run
    try {
      radius = Integer.parseInt(args[1]);
      numThreads = Integer.parseInt(args[2]);
      if (radius < 0) {
        System.err.println("Invalid oil radius");
        System.exit(1);
      }
      if (numThreads < 1) {
        System.err.println("Invalid # threads");
        System.exit(1);
      }
      imageOiler.load();
      imageOiler.process(radius, numThreads);
      imageOiler.save();
    }
    catch (IOException e) {
      e.printStackTrace();
      System.exit(1);
    }
    catch (NumberFormatException e) {
      System.err.println("Invalid # threads");
      System.err.println("Usage: java ConcurrentInvert <input file> <radius> <# threads>");
      System.exit(1);
    }
  }

}
