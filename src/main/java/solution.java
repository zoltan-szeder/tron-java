import java.util.*;
import java.util.Map.*;

class PoolThread extends Thread
{
  // Are we finished yet?
  private boolean finished = false;
  // Which pool
  private ThreadPool mThreadPool;

  /**
   * Creates a new Thread in the pool
   * @param xThreadPool The pool
   */
  public PoolThread( ThreadPool xThreadPool )
  {
    mThreadPool = xThreadPool;
  }

  /**
   * Stop everything
   */
  public void finish()
  {
    finished = true;
  }

  /**
   * Process the jobs
   */
  public void run()
  {
    // Processing loop
    while( !finished )
    {
      Runnable job = mThreadPool.getJob();

      if ( job != null )
      {
        job.run();
      }
    }
  }
}
class ThreadPool
{
  // Locking object
  private final Object mMutex = new Object();
  // Threads in the pool
  private Set<PoolThread> mThreads = new HashSet<>();
  // Jobs to be scheduled
  private Queue<Runnable> mJobs = new LinkedList<>();

  /**
   * Creates a new ThreadPool, with given number of threads.
   * @param xSize Number of threads
   */
  public ThreadPool( int xSize )
  {
    for( ; xSize > 0; xSize-- )
    {
      PoolThread poolThread = new PoolThread( this );
      mThreads.add( poolThread );
      poolThread.start();
    }
  }

  /**
   * Adds a new job to the pool.
   * @param xJob New job
   */
  public void addJob( Runnable xJob )
  {
    synchronized ( mMutex )
    {
      mJobs.add( xJob );
      mMutex.notify();
    }
  }

  /**
   * If the Job pool is empty, it waits for new jobs, and returns them
   * @return The oncoming job.
   */
  Runnable getJob()
  {
    synchronized ( mMutex )
    {
      if( mJobs.isEmpty() )
      {
        waitForJobs();
      }

      if( mJobs.isEmpty() )
      {
        return null;
      }
      else
      {
        return mJobs.remove();
      }
    }
  }

  /**
   * Waits until a new Job is added, or the Pool is finished
   */
  private void waitForJobs()
  {
    try
    {
      mMutex.wait();
    }
    catch ( InterruptedException e )
    {
      e.printStackTrace();
    }
  }

  /**
   * Notifies every thread to stop processing.
   */
  public void finish()
  {
    synchronized ( mMutex )
    {
      for ( PoolThread thread : mThreads )
      {
        thread.finish();
      }
      mMutex.notifyAll();
    }
  }
}
class Board implements Cloneable
{
  // Width of the board
  private int mWidth;
  // Height of the Board
  private int mHeight;
  // The representation of the board
  private char[][] mMap;
  // Players on the Board
  private Map<Character, Cycle> mPlayers = new HashMap<>();

  /**
   * Get the value on the board
   * @param xX X coordinate
   * @param xY Y coordinate
   * @return Value on given position
   */
  public char get( int xX, int xY )
  {
    // Failsafe
    if( xX < 0 || xY < 0 || xX >= mWidth || xY >= mHeight )
    {
      return 100;
    }

    return mMap[xX][xY];
  }

  /**
   * Set the value on the boards coordinate
   * @param xX X coordinate
   * @param xY Y coordinate
   * @param xValue value
   */
  public void set( int xX, int xY, char xValue )
  {
    if( xX < 0 || xY < 0 || xX >= mWidth || xY >= mHeight )
    {
      return;
    }

    mMap[xX][xY] = xValue;
  }

  /**
   * Get the width of the board
   * @return width
   */
  public int getWidth()
  {
    return mWidth;
  }

  /**
   * Get the height of the board
   * @return height
   */
  public int getHeight()
  {
    return mHeight;
  }

  /**
   * Create a new Board with given width and height
   * @param xWidth Width
   * @param xHeight Height
   */
  public Board( int xWidth, int xHeight )
  {
    mWidth = xWidth;
    mHeight = xHeight;
    mMap = new char[mWidth][mHeight];

    // Fill up with zeros
    for ( int x = 0; x < mWidth; x++ )
    {
      for ( int y = 0; y < mHeight; y++ )
      {
        mMap[x][y] = 0;
      }
    }
  }

  /**
   * Get the given player
   * @param xNumber Players number
   * @return The player
   */
  public Cycle getCycle( char xNumber )
  {
    if ( !mPlayers.containsKey( xNumber ) )
    {
      Cycle cycle = new Cycle( this, xNumber );
      mPlayers.put( xNumber, cycle );

      return cycle;
    }

    return mPlayers.get( xNumber );
  }

  /**
   * Creates a copy of this board
   * @return a copy of this board
   */
  @Override
  public Board clone()
  {
    Board board = new Board( mWidth, mHeight );

    for( int x = 0; x < mWidth; x++ )
    {
      for( int y = 0; y < mHeight; y++ )
      {
        board.set( x, y, mMap[x][y] );
      }
    }

    board.mPlayers.putAll( mPlayers );

    return board;
  }

  /**
   * Print the board on the STDERR
   */
  public void dump()
  {
    for ( int y = 0; y < mHeight; y++ )
    {
      for ( int x = 0; x < mWidth; x++ )
      {
        System.err.print( ( int ) mMap[x][y] );
      }
      System.err.println();
    }
  }

  public Collection<Cycle> getCycles()
  {
    return mPlayers.values();
  }
}
class CombinedStrategy implements Strategy
{
  // Semaphore variables
  private final Object mMutex = new Object();
  private int mS;
  private int mV;
  private int mMaxWait = 1750;
  private boolean mTimeout = true;

  // Threads
  private ThreadPool mThreadPool;
  // Response map
  private Map<String, Float> mResult = new HashMap<>();
  // Weighted strategies
  private Map<RunnableStrategy, Float> mStrategies = new HashMap<>();

  /**
   * Creates a new combined strategy according to the given map
   * @param xStrategyMap Weighted strategies
   */
  public CombinedStrategy( Map<Strategy, Float> xStrategyMap )
  {
    mS = xStrategyMap.size();
    mV = mS;
    mThreadPool = new ThreadPool( mS );

    // Wrap the strategies with RunnableStrategy
    for( Map.Entry<Strategy, Float> entry : xStrategyMap.entrySet() )
    {
      Strategy strategy = entry.getKey();
      float lambda = entry.getValue();
      mStrategies.put( new RunnableStrategy( this, strategy ), lambda );
    }
  }

  /**
   * Wait for the ThreadPool to finish or timeout
   */
  protected void waitForResult()
  {
    synchronized ( mMutex )
    {
      try
      {
        mMutex.wait( mMaxWait );
        mTimeout = true;
      }
      catch ( InterruptedException e )
      {
        e.printStackTrace();
      }
    }
  }

  /**
   * Add results to the result map
   * @param xRunnableStrategy Which strategy sent this
   * @param xMap Return values
   */
  public void addResult( RunnableStrategy xRunnableStrategy,  Map<String, Float> xMap )
  {
    // Do nothing on timeout
    if( mTimeout )
    {
      return;
    }
    synchronized ( mMutex )
    {
      float lambda = mStrategies.get( xRunnableStrategy );

      for ( Map.Entry<String, Float> entry : xMap.entrySet() )
      {
        String key = entry.getKey();
        float value = entry.getValue();
        float current = ( mResult.containsKey( key ) ? mResult.get( key ) : 0 );

        current += value*lambda;

        mResult.put( key, current );
      }

      mS--;

      if ( mS == 0 )
      {
        mMutex.notify();
      }
    }
  }

  /**
   * Start the calculations, and wait for the results
   * @param xCoordinates The players coordinates
   * @param xBoard The representation of the board
   * @return The aggregated strategies
   */
  @Override
  public Map<String, Float> calculate( Coordinates xCoordinates, Board xBoard )
  {
    mTimeout = false;
    mS = mV;
    mResult.clear();

    for( RunnableStrategy job : mStrategies.keySet() )
    {
      job.setCoord( xCoordinates, xBoard );
      mThreadPool.addJob( job );
    }

    waitForResult();

    return mResult;
  }
}

class Coordinates
{

  public final int x;
  public final int y;

  public Coordinates( int x0, int y0 )
  {
    x = x0;
    y = y0;
  }
}
class Cycle
{
  // Selected strategy
  private Strategy mStrategy;
  // Previously taken path
  private List<Coordinates> path = new ArrayList<>();
  // Players number
  private char number;
  // Board
  private Board mBoard;
  // Players coordinates
  private Coordinates mCoordinates;

  /**
   * Creates a new Cycle
   * @param xBoard Board
   * @param xNumber Players number
   */
  public Cycle( Board xBoard, char xNumber )
  {
    number = xNumber;
    mBoard = xBoard;
  }

  /**
   * Touch a coordinate on the board
   * @param x0 X position
   * @param y0 Y position
   */
  public void touch( int x0, int y0 )
  {
    mCoordinates = new Coordinates( x0, y0 );
    path.add( mCoordinates );
    mBoard.set( x0, y0, ( char ) ( number + 1 ) );
  }

  /**
   * Destroy the cycle with all its path
   */
  public void destroy(  )
  {
    for ( Coordinates cord : path )
    {
      mBoard.set( cord.x, cord.y, ( char ) 0 );
    }

    path.clear();
  }

  /**
   * Print the players stats on the STDERR
   */
  public void dump()
  {
    System.err.println( ( int ) number + ": X - " + mCoordinates.x +
                          ", Y - " + mCoordinates.y );
  }

  /**
   * Set the strategy of the player
   * @param xStrategy Strategy
   */
  public void setStrategy( Strategy xStrategy )
  {
    mStrategy = xStrategy;
  }

  /**
   * Tell if whether the player has strategy or not
   * @return True if the strategy is set
   */
  public boolean hasStrategy()
  {
    return mStrategy != null;
  }

  /**
   * Decide which direction to go
   * @return The direction
   */
  public String choose()
  {
    Map<String, Float> values = mStrategy.calculate( mCoordinates , mBoard );

    // Select the direction with the maximal value
    String key = null;
    float value = -1;

    for( Entry<String, Float> entry : values.entrySet() )
    {
      if( value < entry.getValue() )
      {
        key = entry.getKey();
        value = entry.getValue();
      }
    }

    return key;
  }

  /**
   * Queries the coordinates of the cycle
   * @return Players coordinates
   */
  public Coordinates getCoordinates()
  {
    return mCoordinates;
  }
}

class DistanceStrategy implements Strategy
{

  /**
   * How much space is in line between the player and the closest wall
   * @param xMCoordinates Given coordination
   * @param xBoard The representation of the board
   * @return The distances
   */
  @Override
  public Map<String, Float> calculate( Coordinates xMCoordinates, Board xBoard )
  {
    int x = xMCoordinates.x;
    int y = xMCoordinates.y;

    Map<String, Float> directions = new HashMap<>();
    directions.put( "LEFT", line( xBoard, x, y, -1, 0 ) );
    directions.put( "UP", line( xBoard, x, y, 0, -1 ) );
    directions.put( "RIGHT", line( xBoard, x, y, 1, 0 ) );
    directions.put( "DOWN", line( xBoard, x, y, 0, 1 ) );
    return directions;
  }

  /**
   * Calculates the lines maximal length in a direction
   * @param xBoard Board
   * @param x Starting X position
   * @param y Starting Y position
   * @param xdir X Velocity
   * @param ydir Y Velocity
   * @return The lines length
   */
  private static float line( Board xBoard, int x, int y, int xdir, int ydir )
  {
    int width = xBoard.getWidth();
    int height = xBoard.getHeight();
    int i = 0;

    x += xdir;
    y += ydir;
    while ( x >= 0 && x < width && y >= 0 && y < height
      && xBoard.get( x, y ) == 0 )
    {
      x += xdir;
      y += ydir;
      i++;
    }
    return i;
  }
}
class Player
{

  public static void main( String args[] )
  {
    Scanner in = new Scanner( System.in );
    Board board = new Board( 30, 20 );

    // game loop
    while ( true )
    {
      int N = in.nextInt(); // total number of players (2 to 4).
      int P = in.nextInt(); // your player number (0 to 3).

      for ( int i = 0; i < N; i++ )
      {

        Cycle cycle = board.getCycle( ( char ) i );

        // starting X coordinate of lightcycle (or -1)
        int X0 = in.nextInt();
        // starting Y coordinate of lightcycle (or -1)
        int Y0 = in.nextInt();
        // starting X coordinate of lightcycle (can be the same as X0
        // if you play before this player)
        int X1 = in.nextInt();
        // starting Y coordinate of lightcycle (can be the same as Y0
        // if you play before this player)
        int Y1 = in.nextInt();

        if ( ( X0 | X1 | Y0 | Y1 ) < 0 )
        {
          cycle.destroy();
        }
        else
        {
          cycle.touch( X1, Y1 );
        }
      }

      Cycle player = board.getCycle( ( char ) P );

      if( ! player.hasStrategy() )
      {
        Map<Strategy, Float> strategies = new HashMap<>(  );
        strategies.put( new DistanceStrategy(), 1f );
        strategies.put( new SpaceStrategy(), 2f );
        strategies.put( new WallHugStrategy(), 2f );
        Strategy combinedStrategy = new CombinedStrategy( strategies );
        player.setStrategy( combinedStrategy );
      }
      // A single line with UP, DOWN, LEFT or RIGHT
      System.out.println( player.choose() );
    }
  }
}

class RunnableStrategy implements Runnable
{
  // Parent strategy
  protected CombinedStrategy mCombinedStrategy;
  // Wrapped strategy
  protected Strategy mStrategy;

  // Coordinates
  protected Coordinates mCoordinates;
  // Board
  protected Board mBoard;

  /**
   * Create a new RunnableStrategy
   * @param xCombinedStrategy Parent strategy
   * @param xStrategy Wrapped strategy
   */
  public RunnableStrategy( CombinedStrategy xCombinedStrategy, Strategy xStrategy )
  {
    mCombinedStrategy = xCombinedStrategy;
    mStrategy = xStrategy;
  }

  /**
   * Set the Players coordinates on the board
   * @param xCoordinates Coordinates
   * @param xBoard The board
   */
  public void setCoord( Coordinates xCoordinates, Board xBoard )
  {
    mCoordinates = xCoordinates;
    mBoard = xBoard;
  }

  /**
   * Calculate the values, normalize it, and add the result to the others
   */
  @Override
  public void run()
  {
    Map<String, Float> result = mStrategy.calculate( mCoordinates, mBoard );
    normalize( result );
    mCombinedStrategy.addResult( this, result );
  }

  /**
   * Normalizes the values to the sum of 1
   * @param xMap The map to normalize
   */
  private void normalize( Map<String, Float> xMap )
  {
    float sum = 0;

    for( float values : xMap.values() )
    {
      sum += values;
    }

    // Avoid division with zero
    if( sum == 0 )
    {
      return;
    }

    for( Map.Entry<String,Float> values : xMap.entrySet() )
    {
      values.setValue( values.getValue() / sum );
    }
  }
}

class SpaceStrategy implements Strategy
{

  /**
   * Calculates the space next to the player.
   * @param xCoordinates Players coordinate
   * @param xBoard The representation of the board
   * @return The free space in each direction
   */
  @Override
  public Map<String, Float> calculate( Coordinates xCoordinates, Board xBoard )
  {
    int x = xCoordinates.x;
    int y = xCoordinates.y;
    int maxdist = 12;
    Board board = xBoard.clone();

    Map<String, Float> spaces = new HashMap<>();
    spaces.put( "LEFT", space( board, x - 1, y, maxdist ) );
    spaces.put( "UP", space( board, x, y - 1, maxdist ) );
    spaces.put( "RIGHT", space( board, x + 1, y, maxdist ) );
    spaces.put( "DOWN", space( board, x, y + 1, maxdist ) );

    return spaces;
  }

  /**
   * Recursively calculates the free space from a point with flooding algorithm
   * @param xBoard The Board
   * @param x X coordinate
   * @param y Y coordinate
   * @param s Maximum number of steps
   * @return Area of free space
   */
  private float space( Board xBoard, int x, int y, int s )
  {
    int width = xBoard.getWidth();
    int height = xBoard.getHeight();
    if ( s <= 0 || x < 0 || x >= width || y < 0 || y >= height )
    {
      return 0;
    }

    int value = xBoard.get( x, y );

    if( value != 0 )
    {
      return 0;
    }

    int i = 1;

    xBoard.set( x, y, ( char ) -1 );

    i += space( xBoard, x + 1, y, s - 1 );
    i += space( xBoard, x - 1, y, s - 1 );
    i += space( xBoard, x, y + 1, s - 1 );
    i += space( xBoard, x, y - 1, s - 1 );

    return i;
  }
}
interface Strategy
{
  String LEFT = "LEFT";
  String RIGHT = "RIGHT";
  String UP = "UP";
  String DOWN = "DOWN";

  /**
   * Calculates the optimal reaction
   * @param xCoordinates Given coordination
   * @param xBoard The representation of the board
   * @return The weighted responses
   */
  Map<String, Float> calculate( Coordinates xCoordinates, Board xBoard );
}
class WallHugStrategy implements Strategy
{

  @Override
  public Map<String, Float> calculate( Coordinates xCoordinates, Board xBoard )
  {
    Map<String, Float> map = new HashMap<>();

    int x = xCoordinates.x;
    int y = xCoordinates.y;
    int left = 1, right = 1, up = 1, down = 1;

    // Top right
    if( xBoard.get( x+1, y-1 ) > 0 )
    {
      right++;
      up++;
    }
    // Bottom right
    if( xBoard.get( x+1, y+1 ) > 0 )
    {
      right++;
      down++;
    }
    // Bottom left
    if( xBoard.get( x-1, y+1 ) > 0 )
    {
      left++;
      down++;
    }
    // Top left
    if( xBoard.get( x-1, y-1 ) > 0 )
    {
      left++;
      up++;
    }

    // Flag dangerous routes
    up = ( up == 3 ? 1 : up );
    right = ( right == 3 ? 1 : right );
    down = ( down == 3 ? 1 : down );
    left = ( left == 3 ? 1 : left );

    // Delete suicide routes
    if( xBoard.get( x, y - 1 ) > 0 )
    {
      up = 0;
    }
    if( xBoard.get( x + 1, y ) > 0 )
    {
      right = 0;
    }
    if( xBoard.get( x, y + 1 ) > 0 )
    {
      down = 0;
    }
    if( xBoard.get( x - 1, y ) > 0 )
    {
      left = 0;
    }

    map.put( UP, ( float ) up );
    map.put( RIGHT, ( float ) right );
    map.put( DOWN, ( float ) down );
    map.put( LEFT, ( float ) left );

    return map;
  }
}
