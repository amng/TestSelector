import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;


public class Loader extends JPanel{

    private final int FIRST_STATE = 0;
    private final int SECOND_STATE = 1;
    private final int THIRD_STATE = 2;
    private final int MAX_SIZE = 270;
    private final int MIN_SIZE = 40;
    private int min = 270;
    private int max = 40;
    private int current_state = 0;
    private boolean is_animating = false;
    Color spinner_color = Color.white;

    /**Stop animation*/
    public void stop_animation(){
        this.is_animating = false;
    }

    /**
     *  Go to next animation state
     *  1st state: no change in arc size
     *  2nd state: arc grows
     *  3rd state: arc shrink
     */
    private void nextState(){
        this.current_state++;
        if(this.current_state > THIRD_STATE)
            this.current_state = FIRST_STATE;
    }

    /**
     * Do the animation having in account the states
     * until the stop_animation method is called
     */
    public void start_animation(){
        this.is_animating = true;
        new Thread(()->{
            while(is_animating) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if(min == 0){
                    nextState();
                }
                min -= 3;
                switch (current_state){
                    case FIRST_STATE: //JUST ROTATE
                        min -= 2;
                        max = MIN_SIZE;
                        break;
                    case SECOND_STATE: //ROTATE AND GET BIGGER
                        min -= 2;
                        if(max < MAX_SIZE)
                            max += 2;
                        break;
                    case THIRD_STATE: //ROTATE AND GET SMALLER
                        min -= 1;
                        if(max > MIN_SIZE)
                            max -= 2;
                        break;
                }
                if(min < 0){
                    min = 360;
                }
                Loader.this.repaint();
            }
        }).start();
    }

    /**
     * Draw the arc with the parameters changed in the animation thread
     * */
    @Override
    public void paintComponent(Graphics graphics){
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        int centerX = width/2;
        int centerY = height/2;

        int radius = 0;
        if(width < height)
            radius = width/2-10;
        else
            radius = height/2-10;
        g2.setStroke(new BasicStroke(5, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
        g2.setColor(spinner_color);
        g2.draw(new Arc2D.Double(centerX, centerY,
                radius,
                radius,
                min, max,
                Arc2D.OPEN));
    }
}
