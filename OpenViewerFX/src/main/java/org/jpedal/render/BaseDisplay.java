/*
 * ===========================================
 * Java Pdf Extraction Decoding Access Library
 * ===========================================
 *
 * Project Info:  http://www.idrsolutions.com
 * Help section for developers at http://www.idrsolutions.com/support/
 *
 * (C) Copyright 1997-2016 IDRsolutions and Contributors.
 *
 * This file is part of JPedal/JPDF2HTML5
 *
     This library is free software; you can redistribute it and/or
    modify it under the terms of the GNU Lesser General Public
    License as published by the Free Software Foundation; either
    version 2.1 of the License, or (at your option) any later version.

    This library is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
    Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public
    License along with this library; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA


 *
 * ---------------
 * BaseDisplay.java
 * ---------------
 */
package org.jpedal.render;

import com.idrsolutions.pdf.color.blends.BlendMode;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.jpedal.color.PdfColor;
import org.jpedal.color.PdfPaint;
import org.jpedal.exception.PdfException;
import org.jpedal.external.FontHandler;
import org.jpedal.fonts.PdfFont;
import org.jpedal.fonts.glyph.PdfGlyph;
import org.jpedal.io.ObjectStore;
import org.jpedal.objects.GraphicsState;
import org.jpedal.objects.PdfShape;
import org.jpedal.objects.raw.PdfDictionary;
import org.jpedal.parser.DecoderOptions;
import org.jpedal.utils.LogWriter;
import org.jpedal.utils.repositories.Vector_Int;
import org.jpedal.utils.repositories.Vector_Object;
import org.jpedal.utils.repositories.generic.Vector_Rectangle_Int;

public abstract class BaseDisplay implements DynamicVectorRenderer {

    /**holds object type*/
    protected Vector_Int objectType;

    /**default array size*/
    protected static final int defaultSize=5000;

    protected int type;

    boolean isType3Font;

    /**set flag to show if we add a background*/
    protected boolean addBackground = true;

    /**holds rectangular outline to test in redraw*/
    protected Vector_Rectangle_Int areas;

    protected ObjectStore objectStoreRef;

    protected int currentItem = -1;

    //Used purely to keep track of rendering for colour change functionality
    protected static int itemToRender = -1;

    //used to track end of PDF page in display
    protected static int endItem=-1;

    Area lastClip;

    boolean hasClips;

    int blendMode=PdfDictionary.Normal;

    /**shows if colours over-ridden for type3 font*/
    boolean colorsLocked;

    Graphics2D g2;

    //used by type3 fonts as identifier
    String rawKey;

    /**global colours if set*/
    PdfPaint fillCol,strokeCol;

    public int rawPageNumber;

    public static boolean invertHighlight;

    boolean isPrinting;

    org.jpedal.external.ImageHandler customImageHandler;

    org.jpedal.external.ColorHandler customColorHandler;

    double cropX, cropH;

    float scaling=1, lastScaling;

    /**initial Q & D object to hold data*/
    protected Vector_Object pageObjects;

    protected final Map<Integer, String> imageIDtoName=new HashMap<Integer, String>(10);

    /**real size of pdf page */
    int w, h;

    /**background color*/
    protected Color backgroundColor = Color.WHITE;
    protected static Color textColor;
    protected static int colorThresholdToReplace = 255;
    protected static boolean enhanceFractionalLines = true;

    protected boolean changeLineArtAndText;

    /**allow user to control*/
    public static RenderingHints userHints;

    @Override
    public void setG2(final Graphics2D g2) {
    	this.g2 = g2;
    	//If user hints has been defined use these values.
    	if(userHints!=null){
    		this.g2.setRenderingHints(userHints);
    	}
    }

    @Override
    public void init(final int width, final int height, final Color backgroundColor) {
    	w = width;
    	h = height;
    	this.backgroundColor = backgroundColor;
    }

    @Override
    public void paintBackground(final Shape dirtyRegion) {
    	if (addBackground && g2!=null){
    			g2.setColor(backgroundColor);

    			if (dirtyRegion == null) {
    				g2.fill(new Rectangle(0, 0, (int) (w * scaling), (int) (h * scaling)));
    			} else {
    				g2.fill(dirtyRegion);
    			}
    		}
    	}


    protected static boolean checkColorThreshold(final int col){

    	final int r = (col)&0xFF;
		final int g = (col>>8)&0xFF;
		final int b = (col>>16)&0xFF;

        return r <= colorThresholdToReplace && g <= colorThresholdToReplace && b <= colorThresholdToReplace;
    }

    void renderEmbeddedText(final int text_fill_type, final Object rawglyph, final int glyphType,
	    final AffineTransform glyphAT, final Rectangle textHighlight,
	    PdfPaint strokePaint, PdfPaint fillPaint,
	    final float strokeOpacity, final float fillOpacity, final int lineWidth) {

        //ensure stroke only shows up
        float strokeOnlyLine = 0;
        if (text_fill_type == GraphicsState.STROKE && lineWidth >= 1.0) {
            strokeOnlyLine = lineWidth;
        }

        //get glyph to draw
        final PdfGlyph glyph = (PdfGlyph) rawglyph;

        final AffineTransform at = g2.getTransform();

        //and also as flat values so we can test below
        final double[] affValues = new double[6];
        at.getMatrix(affValues);

        if (glyph != null) {

            //set transform
            g2.transform(glyphAT);

            //type of draw operation to use
            final Composite comp = g2.getComposite();

            if ((text_fill_type & GraphicsState.FILL) == GraphicsState.FILL) {

            	//If we have an alt text color, its within threshold and not an additional item, use alt color
            	if(textColor!=null && (itemToRender==-1 || (endItem==-1 || itemToRender<=endItem)) && checkColorThreshold(fillPaint.getRGB())){
            		fillPaint = new PdfColor(textColor.getRed(), textColor.getGreen(), textColor.getBlue());
            	}

//                fillPaint.setScaling(cropX, cropH, scaling, 0, 0);
                fillPaint.setScaling(cropX, cropH, scaling, (float)glyphAT.getTranslateX(),(float)glyphAT.getTranslateY());

                if (customColorHandler != null) {
                    customColorHandler.setPaint(g2, fillPaint, rawPageNumber, isPrinting);
                } else if (DecoderOptions.Helper != null) {
                    DecoderOptions.Helper.setPaint(g2, fillPaint, rawPageNumber, isPrinting);
                } else {
                    g2.setPaint(fillPaint);
                }

                renderComposite(fillOpacity);

                if (textHighlight != null) {
                    if (invertHighlight) {
                        final Color color = g2.getColor();
                        g2.setColor(new Color(255 - color.getRed(), 255 - color.getGreen(), 255 - color.getBlue()));
                    } else if (DecoderOptions.backgroundColor != null) {
                        g2.setColor(DecoderOptions.backgroundColor);
                    }
                }


                //pass down color for drawing text
                if(glyphType==DynamicVectorRenderer.TYPE3 && !glyph.ignoreColors()){
                    glyph.setT3Colors(strokePaint, fillPaint,false);
                }

                glyph.render(GraphicsState.FILL, g2, scaling, false);

                //reset opacity
                g2.setComposite(comp);

            }

            if (text_fill_type == GraphicsState.STROKE) {
                glyph.setStrokedOnly(true);
            }

            //creates shadow printing to Mac so added work around
            if (DecoderOptions.isRunningOnMac && isPrinting && text_fill_type == GraphicsState.FILLSTROKE) {
            } else if ((text_fill_type & GraphicsState.STROKE) == GraphicsState.STROKE) {

                if (strokePaint != null) {
                	//If we have an alt text color, its within threshold and not an additional item, use alt color
                	if(textColor!=null && (itemToRender==-1 || (endItem==-1 || itemToRender<=endItem)) && checkColorThreshold(strokePaint.getRGB())){
                		strokePaint = new PdfColor(textColor.getRed(), textColor.getGreen(), textColor.getBlue());
                	}
                    strokePaint.setScaling(cropX, cropH, scaling, 0, 0);
                }

                if (customColorHandler != null) {
                    customColorHandler.setPaint(g2, strokePaint, rawPageNumber, isPrinting);
                } else if (DecoderOptions.Helper != null) {
                    DecoderOptions.Helper.setPaint(g2, strokePaint, rawPageNumber, isPrinting);
                } else {
                    g2.setPaint(strokePaint);
                }

                renderComposite(strokeOpacity);

                if (textHighlight != null) {
                    if (invertHighlight) {
                        final Color color = g2.getColor();
                        g2.setColor(new Color(255 - color.getRed(), 255 - color.getGreen(), 255 - color.getBlue()));
                    } else if (DecoderOptions.backgroundColor != null) {
                        g2.setColor(DecoderOptions.backgroundColor);
                    }
                }

                try {
                    glyph.render(GraphicsState.STROKE, g2, strokeOnlyLine, false);
                } catch (final Exception e) {
                    LogWriter.writeLog("Exception: " + e.getMessage());
                }

                //reset opacity
                g2.setComposite(comp);
            }

            //restore transform
            g2.setTransform(at);

        }
    }

    void renderShape(final Shape defaultClip, final int fillType, PdfPaint strokeCol, PdfPaint fillCol,
	    final Stroke shapeStroke, Shape currentShape, final float strokeOpacity,
	    final float fillOpacity) {

    	boolean clipChanged=false;

	final Shape clip = g2.getClip();

	final Composite comp = g2.getComposite();

    
        //check for 1 x 1 complex shape (after scaling) and replace with dot
        if(type ==DISPLAY_SCREEN && currentShape.getBounds().getWidth()*scaling==1 &&
                currentShape.getBounds().getHeight()*scaling==1 && ((BasicStroke)shapeStroke).getLineWidth()*scaling<1) {
            currentShape = new Rectangle(currentShape.getBounds().x, currentShape.getBounds().y, 1, 1);
        }
        
    
	//stroke and fill (do fill first so we don't overwrite Stroke)
	if (fillType == GraphicsState.FILL || fillType == GraphicsState.FILLSTROKE) {
                // Fill color is null if the shape is a pattern
		if (fillCol != null){
                    if((fillCol.getRGB()!=-1) &&
                            //If we have an alt text color, are changing line art as well, its within threshold and not an additional item, use alt color

                         (changeLineArtAndText && textColor != null && !fillCol.isPattern() && (itemToRender == -1 || (endItem == -1 || itemToRender <= endItem)) && checkColorThreshold(fillCol.getRGB()))) {
                            fillCol = new PdfColor(textColor.getRed(), textColor.getGreen(), textColor.getBlue());

                    }

        	    fillCol.setScaling(cropX, cropH, scaling, 0, 0);
                }

	    if (customColorHandler != null) {
	    	customColorHandler.setPaint(g2, fillCol, rawPageNumber, isPrinting);
	    } else if (DecoderOptions.Helper != null) {
            DecoderOptions.Helper.setPaint(g2, fillCol, rawPageNumber, isPrinting);
	    } else {
	    	g2.setPaint(fillCol);
	    }

        renderComposite(fillOpacity);

	    try{
            //thin lines do not appear unless we use fillRect
            final double iw=currentShape.getBounds2D().getWidth();
            final double ih=currentShape.getBounds2D().getHeight();

            if((ih==0d || iw==0d) && ((BasicStroke)g2.getStroke()).getLineWidth()<=1.0f){
                g2.fillRect(currentShape.getBounds().x,currentShape.getBounds().y,currentShape.getBounds().width,currentShape.getBounds().height);
            }else {
                g2.fill(currentShape);
            }

        }catch(final Exception e){
	    	LogWriter.writeLog("Exception " + e + " filling shape");
        }

	    g2.setComposite(comp);
	}

	if ((fillType == GraphicsState.STROKE) || (fillType == GraphicsState.FILLSTROKE)) {

	    //set values for drawing the shape
	    final Stroke currentStroke = g2.getStroke();

	    //fix for using large width on point to draw line
	    if (currentShape.getBounds2D().getWidth() < 1.0f && ((BasicStroke) shapeStroke).getLineWidth() > 10) {
	    	g2.setStroke(new BasicStroke(1));
	    } else {
            //Adjust line width to 1 if less than 1
            //ignore if using T3Display (such as ap image generation in html / svg conversion
            if(enhanceFractionalLines && ((((BasicStroke)shapeStroke).getLineWidth()*scaling<1) &&
                    !(this instanceof T3Display))){
                g2.setStroke(new BasicStroke(1/scaling,((BasicStroke)shapeStroke).getEndCap(), ((BasicStroke)shapeStroke).getLineJoin(), ((BasicStroke)shapeStroke).getMiterLimit(), ((BasicStroke)shapeStroke).getDashArray(), ((BasicStroke)shapeStroke).getDashPhase()));
            }else{
                g2.setStroke(shapeStroke);
            }
        }

	  //If we have an alt text color, are changing line art, its within threshold and not an additional item, use alt color
	    if(changeLineArtAndText && textColor!=null && !strokeCol.isPattern() && (itemToRender==-1 || (endItem==-1 || itemToRender<=endItem)) && checkColorThreshold(strokeCol.getRGB())){
    		strokeCol = new PdfColor(textColor.getRed(), textColor.getGreen(), textColor.getBlue());
    	}

	    strokeCol.setScaling(cropX, cropH, scaling, 0, 0);

	    if (customColorHandler != null) {
	    	customColorHandler.setPaint(g2, strokeCol, rawPageNumber, isPrinting);
	    } else if (DecoderOptions.Helper != null) {
            DecoderOptions.Helper.setPaint(g2, strokeCol, rawPageNumber, isPrinting);
	    } else {
	    	g2.setPaint(strokeCol);
	    }

	    renderComposite(strokeOpacity);

            if(!isPrinting && clip != null && clip.getBounds2D().getWidth()%1 > 0.65f && clip.getBounds2D().getHeight()%1 > 0.1f){
                if(currentShape.getBounds().getWidth() == clip.getBounds().getWidth()){
                    g2.setClip(BaseDisplay.convertPDFClipToJavaClip(new Area(clip)));  //use null or visible screen area
                    clipChanged=true;
                }
	    }

	    //breaks printing so disabled there
	    if (!isPrinting && clip != null && (clip.getBounds2D().getHeight() < 1 || clip.getBounds2D().getWidth() < 1)) {
	    	g2.setClip(defaultClip);  //use null or visible screen area
	    	clipChanged=true;
	    }

	    g2.draw(currentShape);
	    g2.setStroke(currentStroke);
	    g2.setComposite(comp);
	}

	if(clipChanged) {
        g2.setClip(clip);
    }
    }

    void renderImage(final AffineTransform imageAf, BufferedImage image, final float alpha,
            final GraphicsState currentGraphicsState, final float x, final float y) {

        final boolean renderDirect = (currentGraphicsState != null);

        if (image == null || g2 == null) {
            return;
        }

        final AffineTransform before = g2.getTransform();

        final Composite c = g2.getComposite();
        renderComposite(alpha);

        AffineTransform upside_down;

        float CTM[][] = new float[3][3];
        if (currentGraphicsState != null) {
            CTM = currentGraphicsState.CTM;
        } else {
            double[] values = new double[6];
            imageAf.getMatrix(values);
            CTM[0][0] = (float) values[0];
            CTM[0][1] = (float) values[1];
            CTM[1][0] = (float) values[2];
            CTM[1][1] = (float) values[3];
            CTM[2][0] = x;
            CTM[2][1] = y;
        }

        final int w = image.getWidth();
        final int h = image.getHeight();

        final double[] values = {CTM[0][0] / w, CTM[0][1] / w, -CTM[1][0] / h, -CTM[1][1] / h, 0, 0};
        upside_down = new AffineTransform(values);

        g2.translate(CTM[2][0] + CTM[1][0], CTM[2][1] + CTM[1][1]);

        //allow user to over-ride
        boolean useCustomRenderer = customImageHandler != null;

        if (useCustomRenderer) {
            useCustomRenderer = customImageHandler.drawImageOnscreen(image, 0, upside_down, null, g2, renderDirect, objectStoreRef, isPrinting);

            //exit if done
            if (useCustomRenderer) {
                g2.setComposite(c);
                return;
            }
        }

        //hack to make bw
        if (customColorHandler != null) {
            final BufferedImage newImage = customColorHandler.processImage(image, rawPageNumber, isPrinting);
            if (newImage != null) {
                image = newImage;
            }
        } else if (DecoderOptions.Helper != null) {
            final BufferedImage newImage = DecoderOptions.Helper.processImage(image, rawPageNumber, isPrinting);
            if (newImage != null) {
                image = newImage;
            }
        }

        final Shape g2clip = g2.getClip();
        boolean isClipReset = false;

        //hack to fix clipping issues due to sub-pixels
        if (g2clip != null) {

            final double cy = g2.getClip().getBounds2D().getY();
            final double ch = g2.getClip().getBounds2D().getHeight();
            double diff = image.getHeight() - ch;
            if (diff < 0) {
                diff = -diff;
            }

            if (diff > 0 && diff < 1 && cy < 0 && image.getHeight() > 1 && image.getHeight() < 10) {

                final boolean isSimpleOutline = isSimpleOutline(g2.getClip());

                if (isSimpleOutline) {
                    final double cx = g2.getClip().getBounds2D().getX();
                    final double cw = g2.getClip().getBounds2D().getWidth();

                    g2.setClip(new Rectangle((int) cx, (int) cy, (int) cw, (int) ch));

                    isClipReset = false;
                }
            }
        }

        AffineTransform aff = g2.getTransform();

        double mx = aff.getScaleX();
        double my = aff.getScaleX();
        double sx = upside_down.getScaleX();
        double sy = upside_down.getScaleY();

        //Rotated images can cause issue when scaling up
        //Only handle rotated page with rotation on image
        if ((image.getType() != 0)
                && //Catch issue with images with odd types
                (mx == 0 && my == 0 && sx > 0 && sy < 0)) {
            mx = aff.getShearX();
            my = aff.getShearY();
            sx = Math.abs(sx);
            sy = Math.abs(sy);

            //90 rotation on page
            if (mx > 0 && my > 0) {
                int newWidth = Math.abs((int) ((image.getWidth() * sx) * mx));
                int newHeight = Math.abs((int) ((image.getHeight() * sy) * my));

                //Only use if new image size is large than the original image
                if (newWidth > 0 && newHeight > 0 && newWidth > image.getWidth() && newHeight > image.getHeight()) {
                    BufferedImage bi = new BufferedImage(newWidth, newHeight, image.getType());
                    Graphics2D g = bi.createGraphics();

                    g.setRenderingHints(g2.getRenderingHints());

                    g.drawImage(image, AffineTransform.getScaleInstance(mx * sx, my * sy), null);

                    upside_down.scale(1 / sx, -(1 / sy));
                    aff.scale(1 / mx, -(1 / my));

                    g2.setTransform(aff);

                    image = bi;
                }
            }
        }

        //Draw image as normal
        g2.drawImage(image, upside_down, null);

        if (isClipReset) {
            g2.setClip(g2clip);
        }

        g2.setTransform(before);

        g2.setComposite(c);

    }

    public static boolean isSimpleOutline(final Shape path) {

        int count = 0;
        final PathIterator i = path.getPathIterator(null);
        float[] values = new float[6];

        while (!i.isDone() && count < 6) { //see if rectangle or complex clip
            //Get value before next called otherwise issues with pathIterator ending breaks everything
            int value = i.currentSegment(values);

            i.next();

            count++;

            //If there is a curve, class as complex outline
            if(value==PathIterator.SEG_CUBICTO || value==PathIterator.SEG_QUADTO){
                count = 6;
            }


        }
        return count<6;
    }

    final void renderText(final float x, final float y, final int type, final Area transformedGlyph2,
	    final Rectangle textHighlight, PdfPaint strokePaint,
	    PdfPaint textFillCol, final float strokeOpacity, final float fillOpacity) {

	final Paint currentCol = g2.getPaint();


	//type of draw operation to use
	final Composite comp = g2.getComposite();

	if ((type & GraphicsState.FILL) == GraphicsState.FILL) {

	    if (textFillCol != null) {
	    	//If we have an alt text color, its within threshold and not an additional item, use alt color
	    	if(textColor!=null && (itemToRender==-1 || (endItem==-1 || itemToRender<=endItem)) && checkColorThreshold(textFillCol.getRGB())){
	    		textFillCol = new PdfColor(textColor.getRed(), textColor.getGreen(), textColor.getBlue());
	    	}
		textFillCol.setScaling(cropX, cropH, scaling, x, y);
	    }

	    if (customColorHandler != null) {
		customColorHandler.setPaint(g2, textFillCol, rawPageNumber, isPrinting);
	    } else if (DecoderOptions.Helper != null) {
            DecoderOptions.Helper.setPaint(g2, textFillCol, rawPageNumber, isPrinting);
	    } else {
		g2.setPaint(textFillCol);
	    }

            renderComposite(fillOpacity);

	    if (textHighlight != null) {
		if (invertHighlight) {
		    final Color col = g2.getColor();
		    g2.setColor(new Color(255 - col.getRed(), 255 - col.getGreen(), 255 - col.getBlue()));
		} else if (DecoderOptions.backgroundColor != null) {
		    g2.setColor(DecoderOptions.backgroundColor);
		}
	    }

	    g2.fill(transformedGlyph2);

	    //reset opacity
	    g2.setComposite(comp);

	}

	if ((type & GraphicsState.STROKE) == GraphicsState.STROKE) {

	    if (strokePaint != null) {
	    	//If we have an alt text color, its within threshold and not an additional item, use alt color
	    	if(textColor!=null && (itemToRender==-1 || (endItem==-1 || itemToRender<=endItem)) && checkColorThreshold(strokePaint.getRGB())){
	    		strokePaint = new PdfColor(textColor.getRed(), textColor.getGreen(), textColor.getBlue());
	    	}
		strokePaint.setScaling(cropX + x, cropH + y, scaling, x, y);
	    }

	    if (customColorHandler != null) {
		customColorHandler.setPaint(g2, strokePaint, rawPageNumber, isPrinting);
	    } else if (DecoderOptions.Helper != null) {
            DecoderOptions.Helper.setPaint(g2, strokePaint, rawPageNumber, isPrinting);
	    } else {
		g2.setPaint(strokePaint);
	    }

	    renderComposite(strokeOpacity);

	    if (textHighlight != null) {
		if (invertHighlight) {
		    final Color col = g2.getColor();
		    g2.setColor(new Color(255 - col.getRed(), 255 - col.getGreen(), 255 - col.getBlue()));
		} else if (DecoderOptions.backgroundColor != null) {
		    g2.setColor(DecoderOptions.backgroundColor);
		}
	    }

	    //factor in scaling
	    float lineWidth = (float) (1f / g2.getTransform().getScaleX());

	    if (lineWidth < 0) {
		lineWidth = -lineWidth;
	    }

	    g2.setStroke(new BasicStroke(lineWidth));

	    if (lineWidth < 0.1f) {
		g2.draw(transformedGlyph2);
	    } else {
		g2.fill(transformedGlyph2);
	    }

	    //reset opacity
	    g2.setComposite(comp);
	}

	g2.setPaint(currentCol);
    }
    
    @Override
    public void setScalingValues(final double cropX, final double cropH, final float scaling) {

	this.cropX = cropX;
	this.cropH = cropH;
	this.scaling = scaling;

    }

    @Override
    public void setCustomImageHandler(final org.jpedal.external.ImageHandler customImageHandler) {
	this.customImageHandler = customImageHandler;
    }

    @Override
    public void setCustomColorHandler(final org.jpedal.external.ColorHandler colorController) {
	this.customColorHandler = colorController;
    }

    ////////////////////NOT used except by screen/////////////////////////////////
    /**
     * reset on colorspace change to ensure cached data up to data
     */
    @Override
    public void resetOnColorspaceChange() {
    }

    @Override
    public boolean hasObjectsBehind(final float[][] CTM){

        boolean hasObject = false;

        int x = (int) CTM[2][0];
        int y = (int) CTM[2][1];
        int w = (int) CTM[0][0];
        if (w == 0) {
            w = (int) CTM[0][1];
        }
        int h = (int)CTM[1][1];
        if (h == 0) {
            h = (int) CTM[1][0];
        }

        //if h or w are negative, reverse values
        //as intersects and contains can't cope with it
        if (h < 0) {
            y += h;
            //h = y - h;
        }

        if (w < 0) {
            x += w;
            w = x - w;
        }

        //JavaFX does not store locations so we should just return true
        if(this.areas==null){
            return true;
        }

        final int[][] areas = this.areas.get();
        final int count = areas.length;

            int rx,ry,rw,rh;

        for (int i = 0; i < count; i++) {
            if (areas[i] != null) {

                //find if overlap and exit once found
                rx=areas[i][0];
                ry=areas[i][1];
                rw=areas[i][2];
                rh=areas[i][3];

                //if(rw==0 || rh==0){
                //    continue;
              //  }

                final boolean xOverlap = valueInRange(x, rx, rx + rw) || valueInRange(rx, x, x + w);
                final boolean yOverlap = xOverlap && valueInRange(y, ry, ry + rh) || valueInRange(ry, y, y + h);

                if(xOverlap && yOverlap){ //first match
                    i=count;
                    hasObject=true;
                }

            }
        }

        return hasObject;
    }

    private static boolean valueInRange(final int value, final int min, final int max)
    {
        return (value >= min && value <= max);
    }

    /**
     * operations to do once page done
     */
    @Override
    public void flagDecodingFinished() {
    }

    /**
     * turn object into byte[] so we can move across
     * this way should be much faster than the stadard Java serialise.
     * <br>
     * NOT PART OF API and subject to change (DO NOT USE)
     *
     * @throws java.io.IOException
     */
    @Override
    public byte[] serializeToByteArray(final Set<String> fontsAlreadyOnClient) throws IOException {
	return new byte[0];
    }

    /**
     * HTML, or Image, or Display
     */
    @Override
    public int getType(){
    	return type;
    }

    @Override
    public void drawShape(final PdfShape pdfShape, final GraphicsState currentGraphicsState, final int cmd) {
	//    throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void drawEmbeddedText(final float[][] Trm, final int fontSize, final PdfGlyph embeddedGlyph, final Object javaGlyph, final int type, final GraphicsState gs, final double[] at, final String glyf, final PdfFont currentFontData, final float glyfWidth) {
	//throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void paint(final Rectangle[] highlights, final AffineTransform viewScaling, final Rectangle userAnnot) {
	//   throw new UnsupportedOperationException("Not supported yet.");

    }

    @Override
    public void dispose() {
    }

    @Override
    public int drawImage(final int pageNumber, final BufferedImage image, final GraphicsState currentGraphicsState, final boolean alreadyCached, final String name, final int previousUse) {
	return -1;
    }

    @Override
    public void drawAdditionalObjectsOverPage(final int[] type, final Color[] colors, final Object[] obj) throws PdfException {
    }

    @Override
    public void flushAdditionalObjOnPage() {
    }

    @Override
    public void flush() {
	//throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void drawText(final float[][] Trm, final String text, final GraphicsState currentGraphicsState, final float x, final float y, final Font javaFont) {
	//throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void setGraphicsState(final int fillType, final float value, final int BM) {
	//throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void drawTR(final int value) {
	//throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void drawClip(final GraphicsState currentGraphicsState, final Shape defaultClip, final boolean alwaysDraw) {
	//    throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * used by some custom version of DynamicVectorRenderer
     */
    @Override
    public void writeCustom(final int key, final Object value) {
    }

	/** allow tracking of specific commands**/
	@Override
    public void updateTokenNumber(final int tokenNumber) {

	}

    @Override
    public void setValue(final int key, final int i) {
        switch(key){
        case ALT_BACKGROUND_COLOR:
        	backgroundColor = new Color(i);
        	break;
        case ALT_FOREGROUND_COLOR:
        	textColor = new Color(i);
        	break;
        case FOREGROUND_INCLUDE_LINEART:
            changeLineArtAndText = i > 0;
        	break;
        case COLOR_REPLACEMENT_THRESHOLD:
        	colorThresholdToReplace = i;
        	break;
        case ENHANCE_FRACTIONAL_LINES:
            enhanceFractionalLines = i != 0;
        	break;
        }
    }

    @Override
    public int getValue(final int key) {
        //used by HTML to get font handing mode, etc
        //this is the unused 'dummy' default implementation required for other modes as in Interface
        return -1;
    }

    /**
     * allow user to read
     */
    @Override
    public boolean getBooleanValue(final int key) {
        return false;
    }

    /**
     * only used in HTML5 and SVG conversion
     *
     * @param fontObjID
     * @param s
     * @param potentialWidth
     */
    @Override
    public void saveAdvanceWidth(final int fontObjID, final String s, final int potentialWidth) {

    }

    public static int isRectangle(final Shape bounds) {

        int count = 0;
        final PathIterator i = bounds.getPathIterator(null);

        while (!i.isDone() && count < 8) { //see if rectangle or complex clip
            i.next();
            count++;
        }

        return count;
    }

    /*save shape in array to draw*/
    @Override
    public void drawShape(final Object currentShape, final GraphicsState currentGraphicsState) {
        System.out.println("drawShape in BaseDisplay Should never be called");
    }

    /*save shape in array to draw*/
	@Override
    public void eliminateHiddenText(final Shape currentShape, final GraphicsState gs, final int count, boolean ignoreScaling) {
    }

    private void renderComposite(final float alpha) {

        if(blendMode==PdfDictionary.Normal || blendMode==PdfDictionary.Compatible){
            if (alpha != 1.0f) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }
        }else{/// if (alpha != 1.0f){ - possible fix for 19888 to test

            final Composite comp=new BlendMode(blendMode,alpha);

            g2.setComposite(comp);
        }
    }

    @Override
    public boolean isHTMLorSVG() {
        return false;
    }

    /**
     * Increases clip size without altering input area
     * @param clip The clipping areas that needs increasing
     * @return Area for the modified clip size
     */
    public static Area convertPDFClipToJavaClip(Area clip){

        if (clip != null) {
        //Increase clips size by 1 pixel in all direction as pdf clip includes bounds,
            //java only handles inside of bounds
            double sx = (clip.getBounds2D().getWidth() + 2) / clip.getBounds2D().getWidth();
            double sy = (clip.getBounds2D().getHeight() + 2) / clip.getBounds2D().getHeight();
            double posX = clip.getBounds2D().getX();
            double posY = clip.getBounds2D().getY();

            Area a = (Area) clip.clone();
            a.transform(AffineTransform.getTranslateInstance(-posX, -posY));
            a.transform(AffineTransform.getScaleInstance(sx, sy));
            a.transform(AffineTransform.getTranslateInstance(posX - 1, posY - 1));

            return a;
        }
        return clip;
    }

    @Override
    public FontHandler getFontHandler(){
        return null;
    }
}
