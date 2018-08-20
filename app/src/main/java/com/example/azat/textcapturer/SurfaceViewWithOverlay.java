package com.example.azat.textcapturer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.SurfaceView;

import com.abbyy.mobile.rtr.ITextCaptureService;

// Surface View combined with an overlay showing recognition results and 'progress'
public class SurfaceViewWithOverlay extends SurfaceView {
    private Point[] quads;
    private String[] lines;
    private Rect areaOfInterest;
    private int stability;
    private int scaleNominatorX = 1;
    private int scaleDenominatorX = 1;
    private int scaleNominatorY = 1;
    private int scaleDenominatorY = 1;
    private Paint textPaint;
    private Paint lineBoundariesPaint;
    private Paint backgroundPaint;
    private Paint areaOfInterestPaint;

    public SurfaceViewWithOverlay( Context context )
    {
        super( context );
        this.setWillNotDraw( false );

        lineBoundariesPaint = new Paint();
        lineBoundariesPaint.setStyle( Paint.Style.STROKE );
        lineBoundariesPaint.setARGB( 255, 128, 128, 128 );
        textPaint = new Paint();
        areaOfInterestPaint = new Paint();
        areaOfInterestPaint.setARGB( 100, 0, 0, 0 );
        areaOfInterestPaint.setStyle( Paint.Style.FILL );
    }

    public void setScaleX( int nominator, int denominator )
    {
        scaleNominatorX = nominator;
        scaleDenominatorX = denominator;
    }

    public void setScaleY( int nominator, int denominator )
    {
        scaleNominatorY = nominator;
        scaleDenominatorY = denominator;
    }

    public void setFillBackground( Boolean newValue )
    {
        if( newValue ) {
            backgroundPaint = new Paint();
            backgroundPaint.setStyle( Paint.Style.FILL );
            backgroundPaint.setARGB( 100, 255, 255, 255 );
        } else {
            backgroundPaint = null;
        }
        invalidate();
    }

    public void setAreaOfInterest( Rect newValue )
    {
        areaOfInterest = newValue;
        invalidate();
    }

    public Rect getAreaOfInterest()
    {
        return areaOfInterest;
    }

    public void setLines( ITextCaptureService.TextLine[] lines,
                          ITextCaptureService.ResultStabilityStatus resultStatus )
    {
        if( lines != null && scaleDenominatorX > 0 && scaleDenominatorY > 0 ) {
            this.quads = new Point[lines.length * 4];
            this.lines = new String[lines.length];
            for( int i = 0; i < lines.length; i++ ) {
                ITextCaptureService.TextLine line = lines[i];
                for( int j = 0; j < 4; j++ ) {
                    this.quads[4 * i + j] = new Point(
                            ( scaleNominatorX * line.Quadrangle[j].x ) / scaleDenominatorX,
                            ( scaleNominatorY * line.Quadrangle[j].y ) / scaleDenominatorY
                    );
                }
                this.lines[i] = line.Text;
            }
            switch( resultStatus ) {
                case NotReady:
                    textPaint.setARGB( 255, 128, 0, 0 );
                    break;
                case Tentative:
                    textPaint.setARGB( 255, 128, 0, 0 );
                    break;
                case Verified:
                    textPaint.setARGB( 255, 128, 64, 0 );
                    break;
                case Available:
                    textPaint.setARGB( 255, 128, 128, 0 );
                    break;
                case TentativelyStable:
                    textPaint.setARGB( 255, 64, 128, 0 );
                    break;
                case Stable:
                    textPaint.setARGB( 255, 0, 128, 0 );
                    break;
            }
            stability = resultStatus.ordinal();

        } else {
            stability = 0;
            this.lines = null;
            this.quads = null;
        }
        this.invalidate();
    }

    @Override
    protected void onDraw( Canvas canvas )
    {
        super.onDraw( canvas );
        int width = canvas.getWidth();
        int height = canvas.getHeight();
        canvas.save();
        // If there is any result
        if( lines != null ) {
            // Shade (whiten) the background when stable
            if( backgroundPaint != null ) {
                canvas.drawRect( 0, 0, width, height, backgroundPaint );
            }
        }
        if( areaOfInterest != null ) {
            // Shading and clipping the area of interest
            int left = ( areaOfInterest.left * scaleNominatorX ) / scaleDenominatorX;
            int right = ( areaOfInterest.right * scaleNominatorX ) / scaleDenominatorX;
            int top = ( areaOfInterest.top * scaleNominatorY ) / scaleDenominatorY;
            int bottom = ( areaOfInterest.bottom * scaleNominatorY ) / scaleDenominatorY;
            canvas.drawRect( 0, 0, width, top, areaOfInterestPaint );
            canvas.drawRect( 0, bottom, width, height, areaOfInterestPaint );
            canvas.drawRect( 0, top, left, bottom, areaOfInterestPaint );
            canvas.drawRect( right, top, width, bottom, areaOfInterestPaint );
            canvas.drawRect( left, top, right, bottom, lineBoundariesPaint );
            canvas.clipRect( left, top, right, bottom );
        }
        // If there is any result
        if( lines != null ) {
            // Draw the text lines
            for( int i = 0; i < lines.length; i++ ) {
                // The boundaries
                int j = 4 * i;
                Path path = new Path();
                Point p = quads[j + 0];
                path.moveTo( p.x, p.y );
                p = quads[j + 1];
                path.lineTo( p.x, p.y );
                p = quads[j + 2];
                path.lineTo( p.x, p.y );
                p = quads[j + 3];
                path.lineTo( p.x, p.y );
                path.close();
                canvas.drawPath( path, lineBoundariesPaint );

                // The skewed text (drawn by coordinate transform)
                canvas.save();
                Point p0 = quads[j + 0];
                Point p1 = quads[j + 1];
                Point p3 = quads[j + 3];

                int dx1 = p1.x - p0.x;
                int dy1 = p1.y - p0.y;
                int dx2 = p3.x - p0.x;
                int dy2 = p3.y - p0.y;

                int sqrLength1 = dx1 * dx1 + dy1 * dy1;
                int sqrLength2 = dx2 * dx2 + dy2 * dy2;

                double angle = 180 * Math.atan2( dy2, dx2 ) / Math.PI;
                double xskew = ( dx1 * dx2 + dy1 * dy2 ) / Math.sqrt( sqrLength2 );
                double yskew = Math.sqrt( sqrLength1 - xskew * xskew );

                textPaint.setTextSize( (float) yskew );
                String line = lines[i];
                Rect textBounds = new Rect();
                textPaint.getTextBounds( lines[i], 0, line.length(), textBounds );
                double xscale = Math.sqrt( sqrLength2 ) / textBounds.width();

                canvas.translate( p0.x, p0.y );
                canvas.rotate( (float) angle );
                canvas.skew( -(float) ( xskew / yskew ), 0.0f );
                canvas.scale( (float) xscale, 1.0f );

                canvas.drawText( lines[i], 0, 0, textPaint );
                canvas.restore();
            }
        }
        canvas.restore();

        // Draw the 'progress'
        if( stability > 0 ) {
            int r = width / 50;
            int y = height - 175 - 2 * r;
            for( int i = 0; i < stability; i++ ) {
                int x = width / 2 + 3 * r * ( i - 2 );
                canvas.drawCircle( x, y, r, textPaint );
            }
        }
    }
}