/* source: http://marathon.csee.usf.edu/edge/edge_detection.html */
/* URL: ftp://figment.csee.usf.edu/pub/Edge_Comparison/source_code/canny.src */

#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <sim.sh>
#include <c_typed_queue.sh>

#define VERBOSE 0

#define NOEDGE 255
#define POSSIBLE_EDGE 128
#define EDGE 0
#define BOOSTBLURFACTOR 90.0
#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif
#define SIGMA 0.6
#define TLOW  0.3
#define THIGH 0.8

#define COLS 2704
#define ROWS 1520
#define SIZE COLS*ROWS

/* upper bound for the size of the gaussian kernel
 * SIGMA must be less than 4.0
 * check for 'windowsize' below
 */
#define WINSIZE 21

typedef unsigned char img[SIZE];
typedef short int calcsIm[SIZE];

DEFINE_I_TYPED_SENDER(img, img) // receiver interface
DEFINE_I_TYPED_SENDER(calcsIm, calcsIm)

DEFINE_I_TYPED_RECEIVER(calcsIm, calcsIm)
DEFINE_I_TYPED_RECEIVER(img, img) // sender interface 

DEFINE_I_TYPED_TRANCEIVER(calcsIm, calcsIm)
DEFINE_I_TYPED_TRANCEIVER(img, img)

DEFINE_C_TYPED_QUEUE(img, img) // image channel
DEFINE_C_TYPED_QUEUE(calcsIm, calcsIm) // short int channel

behavior DataIn(i_img_receiver Pin, i_img_sender Pout) {
   img inImg, outImg;
  
   void main(void)
   {
      while(true) {   
         Pin.receive(&inImg);
         outImg = inImg;
         Pout.send(outImg);
      }
   }
};

behavior DataOut(i_img_receiver Pin, i_img_sender Pout) {
   img inImg, outImg;

   void main(void)
   {
      while(true) {
         Pin.receive(&inImg);
         outImg = inImg;
         Pout.send(outImg);
      }
   }
};

behavior Gaussian_Smooth(i_img_receiver Pin, i_calcsIm_sender smthIm) {

   img inImg;
   calcsIm smoothIm;

   void make_gaussian_kernel(float sigma, float *kernel, int *windowsize);  

	/*******************************************************************************
	* PROCEDURE: gaussian_smooth
	* PURPOSE: Blur an image with a gaussian filter.
	* NAME: Mike Heath
	* DATE: 2/15/96
	*******************************************************************************/
	void gaussian_smooth(unsigned char *image, int rows, int cols, float sigma,
		short int *smoothedim)
	{
	   int r, c, rr, cc,     /* Counter variables. */
	      windowsize,        /* Dimension of the gaussian kernel. */
	      center;            /* Half of the windowsize. */
	   float tempim[SIZE]    /* Buffer for separable filter gaussian smoothing. */
			= {0.0},
		 kernel[WINSIZE] /* A one dimensional gaussian kernel. */
			= {0.0},
		 dot,            /* Dot product summing variable. */
		 sum;            /* Sum of the kernel weights variable. */
           
	   /****************************************************************************
	   * Create a 1-dimensional gaussian smoothing kernel.
	   ****************************************************************************/
	   if(VERBOSE) printf("   Computing the gaussian smoothing kernel.\n");
	   make_gaussian_kernel(sigma, kernel, &windowsize);
	   center = windowsize / 2;

	   /****************************************************************************
	   * Blur in the x - direction.
	   ****************************************************************************/
	   if(VERBOSE) printf("   Blurring the image in the X-direction.\n");
	   for(r=0;r<rows;r++){
	      for(c=0;c<cols;c++){
		 dot = 0.0;
		 sum = 0.0;
		 for(cc=(-center);cc<=center;cc++){
		    if(((c+cc) >= 0) && ((c+cc) < cols)){
		       dot += (float)image[r*cols+(c+cc)] * kernel[center+cc];
		       sum += kernel[center+cc];
		    }
		 }
		 tempim[r*cols+c] = dot/sum;
	      }
	   }

	   /****************************************************************************
	   * Blur in the y - direction.
	   ****************************************************************************/
	   if(VERBOSE) printf("   Blurring the image in the Y-direction.\n");
	   for(c=0;c<cols;c++){
	      for(r=0;r<rows;r++){
		 sum = 0.0;
		 dot = 0.0;
		 for(rr=(-center);rr<=center;rr++){
		    if(((r+rr) >= 0) && ((r+rr) < rows)){
		       dot += tempim[(r+rr)*cols+c] * kernel[center+rr];
		       sum += kernel[center+rr];
		    }
		 }
		 smoothedim[r*cols+c] = (short int)(dot*BOOSTBLURFACTOR/sum + 0.5);
	      }
	   }
	}


	/*******************************************************************************
	* PROCEDURE: make_gaussian_kernel
	* PURPOSE: Create a one dimensional gaussian kernel.
	* NAME: Mike Heath
	* DATE: 2/15/96
	*******************************************************************************/
	void make_gaussian_kernel(float sigma, float *kernel, int *windowsize)
	{
	   int i, center;
	   float x, fx, sum=0.0;

	   *windowsize = 1 + 2 * ceil(2.5 * sigma);
	   center = (*windowsize) / 2;

	   if(VERBOSE) printf("      The kernel has %d elements.\n", *windowsize);

	   for(i=0;i<(*windowsize);i++){
	      x = (float)(i - center);
	      fx = pow(2.71828, -0.5*x*x/(sigma*sigma)) / (sigma * sqrt(6.2831853));
	      kernel[i] = fx;
	      sum += fx;
	   }

	   for(i=0;i<(*windowsize);i++) kernel[i] /= sum;

	   if(VERBOSE){
	      printf("The filter coefficients are:\n");
	      for(i=0;i<(*windowsize);i++)
		 printf("kernel[%d] = %f\n", i, kernel[i]);
	   }
	}
 
  void main(void) {
     Pin.receive(&inImg);
     gaussian_smooth(inImg, ROWS, COLS, SIGMA, smoothIm);
     //printf("test smoothim\n");
     smthIm.send(smoothIm);
  }

};

behavior Derivative(i_calcsIm_receiver smthIm, i_calcsIm_sender deltax, i_calcsIm_sender deltay, i_calcsIm_sender deltax2, i_calcsIm_sender deltay2) {
   calcsIm smoothIm, dx, dy;   
   
	/*******************************************************************************
	* PROCEDURE: derivative_x_y
	* PURPOSE: Compute the first derivative of the image in both the x any y
	* directions. The differential filters that are used are:
	*
	*                                          -1
	*         dx =  -1 0 +1     and       dy =  0
	*                                          +1
	*
	* NAME: Mike Heath
	* DATE: 2/15/96
	*******************************************************************************/
	void derivative_x_y(short int *smoothedim, int rows, int cols,
		short int *delta_x, short int *delta_y)
	{
	   int r, c, pos;

	   /****************************************************************************
	   * Compute the x-derivative. Adjust the derivative at the borders to avoid
	   * losing pixels.
	   ****************************************************************************/
	   if(VERBOSE) printf("   Computing the X-direction derivative.\n");
	   for(r=0;r<rows;r++){
	      pos = r * cols;
	      delta_x[pos] = smoothedim[pos+1] - smoothedim[pos];
	      pos++;
	      for(c=1;c<(cols-1);c++,pos++){
		 delta_x[pos] = smoothedim[pos+1] - smoothedim[pos-1];
	      }
	      delta_x[pos] = smoothedim[pos] - smoothedim[pos-1];
	   }

	   /****************************************************************************
	   * Compute the y-derivative. Adjust the derivative at the borders to avoid
	   * losing pixels.
	   ****************************************************************************/
	   if(VERBOSE) printf("   Computing the Y-direction derivative.\n");
	   for(c=0;c<cols;c++){
	      pos = c;
	      delta_y[pos] = smoothedim[pos+cols] - smoothedim[pos];
	      pos += cols;
	      for(r=1;r<(rows-1);r++,pos+=cols){
		 delta_y[pos] = smoothedim[pos+cols] - smoothedim[pos-cols];
	      }
	      delta_y[pos] = smoothedim[pos] - smoothedim[pos-cols];
	   }
	}

   void main(void) {
      smthIm.receive(&smoothIm);
      derivative_x_y(smoothIm, ROWS, COLS, dx, dy);
      //printf("test deri\n");
      deltax.send(dx);
      deltay.send(dy);
      deltax2.send(dx);
      deltay2.send(dy);
   }
};

behavior Magnitude(i_calcsIm_receiver deltax, i_calcsIm_receiver deltay, i_calcsIm_sender magOut, i_calcsIm_sender magOut2) {
   calcsIm dx, dy, mag;
       
	/*******************************************************************************
	* PROCEDURE: magnitude_x_y
	* PURPOSE: Compute the magnitude of the gradient. This is the square root of
	* the sum of the squared derivative values.
	* NAME: Mike Heath
	* DATE: 2/15/96
	*******************************************************************************/
	void magnitude_x_y(short int *delta_x, short int *delta_y, int rows, int cols,
		short int *magnitude)
	{
	   int r, c, pos, sq1, sq2;

	   for(r=0,pos=0;r<rows;r++){
	      for(c=0;c<cols;c++,pos++){
		 sq1 = (int)delta_x[pos] * (int)delta_x[pos];
		 sq2 = (int)delta_y[pos] * (int)delta_y[pos];
		 magnitude[pos] = (short)(0.5 + sqrt((float)sq1 + (float)sq2));
	      }
	   }
	}

   void main(void) {
      deltax.receive(&dx);
      deltay.receive(&dy);
      magnitude_x_y(dx, dy, ROWS, COLS, mag);
      //printf("test mag\n");
      magOut.send(mag);
      magOut2.send(mag);
   }
};

behavior NMS(i_calcsIm_receiver magnitude, i_calcsIm_receiver deltax, i_calcsIm_receiver deltay, i_img_sender imgOut) {

	/*******************************************************************************
	* PROCEDURE: non_max_supp
	* PURPOSE: This routine applies non-maximal suppression to the magnitude of
	* the gradient image.
	* NAME: Mike Heath
	* DATE: 2/15/96
	*******************************************************************************/
	void non_max_supp(short *mag, short *gradx, short *grady, int nrows, int ncols,
	    unsigned char *result)
	{
	    int rowcount, colcount,count;
	    short *magrowptr,*magptr;
	    short *gxrowptr,*gxptr;
	    short *gyrowptr,*gyptr,z1,z2;
	    short m00,gx,gy;
	    float mag1,mag2,xperp,yperp;
	    unsigned char *resultrowptr, *resultptr;
            
	   /****************************************************************************
	   * Zero the edges of the result image.
	   ****************************************************************************/
	    for(count=0,resultrowptr=result,resultptr=result+ncols*(nrows-1);
		count<ncols; resultptr++,resultrowptr++,count++){
		*resultrowptr = *resultptr = (unsigned char) 0;
	    }

	    for(count=0,resultptr=result,resultrowptr=result+ncols-1;
		count<nrows; count++,resultptr+=ncols,resultrowptr+=ncols){
		*resultptr = *resultrowptr = (unsigned char) 0;
	    }

	   /****************************************************************************
	   * Suppress non-maximum points.
	   ****************************************************************************/
	   for(rowcount=1,magrowptr=mag+ncols+1,gxrowptr=gradx+ncols+1,
	      gyrowptr=grady+ncols+1,resultrowptr=result+ncols+1;
	      rowcount<=nrows-2;	// bug fix 1/6/2020, RD
	      rowcount++,magrowptr+=ncols,gyrowptr+=ncols,gxrowptr+=ncols,
	      resultrowptr+=ncols){
	      for(colcount=1,magptr=magrowptr,gxptr=gxrowptr,gyptr=gyrowptr,
		 resultptr=resultrowptr;colcount<=ncols-2;	// bug fix 1/6/2020, RD
		 colcount++,magptr++,gxptr++,gyptr++,resultptr++){
		 m00 = *magptr;
		 if(m00 == 0){
		    *resultptr = (unsigned char) NOEDGE;
		 }
		 else{
		    xperp = -(gx = *gxptr)/((float)m00);
		    yperp = (gy = *gyptr)/((float)m00);
		 }

		 if(gx >= 0){
		    if(gy >= 0){
			    if (gx >= gy)
			    {
				/* 111 */
				/* Left point */
				z1 = *(magptr - 1);
				z2 = *(magptr - ncols - 1);

				mag1 = (m00 - z1)*xperp + (z2 - z1)*yperp;

				/* Right point */
				z1 = *(magptr + 1);
				z2 = *(magptr + ncols + 1);

				mag2 = (m00 - z1)*xperp + (z2 - z1)*yperp;
			    }
			    else
			    {
				/* 110 */
				/* Left point */
				z1 = *(magptr - ncols);
				z2 = *(magptr - ncols - 1);

				mag1 = (z1 - z2)*xperp + (z1 - m00)*yperp;

				/* Right point */
				z1 = *(magptr + ncols);
				z2 = *(magptr + ncols + 1);

				mag2 = (z1 - z2)*xperp + (z1 - m00)*yperp;
			    }
			}
			else
			{
			    if (gx >= -gy)
			    {
				/* 101 */
				/* Left point */
				z1 = *(magptr - 1);
				z2 = *(magptr + ncols - 1);

				mag1 = (m00 - z1)*xperp + (z1 - z2)*yperp;

				/* Right point */
				z1 = *(magptr + 1);
				z2 = *(magptr - ncols + 1);

				mag2 = (m00 - z1)*xperp + (z1 - z2)*yperp;
			    }
			    else
			    {
				/* 100 */
				/* Left point */
				z1 = *(magptr + ncols);
				z2 = *(magptr + ncols - 1);

				mag1 = (z1 - z2)*xperp + (m00 - z1)*yperp;

				/* Right point */
				z1 = *(magptr - ncols);
				z2 = *(magptr - ncols + 1);

				mag2 = (z1 - z2)*xperp  + (m00 - z1)*yperp;
			    }
			}
		    }
		    else
		    {
			if ((gy = *gyptr) >= 0)
			{
			    if (-gx >= gy)
			    {
				/* 011 */
				/* Left point */
				z1 = *(magptr + 1);
				z2 = *(magptr - ncols + 1);

				mag1 = (z1 - m00)*xperp + (z2 - z1)*yperp;

				/* Right point */
				z1 = *(magptr - 1);
				z2 = *(magptr + ncols - 1);

				mag2 = (z1 - m00)*xperp + (z2 - z1)*yperp;
			    }
			    else
			    {
				/* 010 */
				/* Left point */
				z1 = *(magptr - ncols);
				z2 = *(magptr - ncols + 1);

				mag1 = (z2 - z1)*xperp + (z1 - m00)*yperp;

				/* Right point */
				z1 = *(magptr + ncols);
				z2 = *(magptr + ncols - 1);

				mag2 = (z2 - z1)*xperp + (z1 - m00)*yperp;
			    }
			}
			else
			{
			    if (-gx > -gy)
			    {
				/* 001 */
				/* Left point */
				z1 = *(magptr + 1);
				z2 = *(magptr + ncols + 1);

				mag1 = (z1 - m00)*xperp + (z1 - z2)*yperp;

				/* Right point */
				z1 = *(magptr - 1);
				z2 = *(magptr - ncols - 1);

				mag2 = (z1 - m00)*xperp + (z1 - z2)*yperp;
			    }
			    else
			    {
				/* 000 */
				/* Left point */
				z1 = *(magptr + ncols);
				z2 = *(magptr + ncols + 1);

				mag1 = (z2 - z1)*xperp + (m00 - z1)*yperp;

				/* Right point */
				z1 = *(magptr - ncols);
				z2 = *(magptr - ncols - 1);

				mag2 = (z2 - z1)*xperp + (m00 - z1)*yperp;
			    }
			}
		    }

		    /* Now determine if the current point is a maximum point */

		    if ((mag1 > 0.0) || (mag2 > 0.0))
		    {
			*resultptr = (unsigned char) NOEDGE;
		    }
		    else
		    {
			if (mag2 == 0.0)
			    *resultptr = (unsigned char) NOEDGE;
			else
			    *resultptr = (unsigned char) POSSIBLE_EDGE;
		    }
		}
	    }
	}
 
   void main(void) {
      calcsIm mag, dx, dy;
      img result;
      //printf("test nms before receive\n");
      magnitude.receive(&mag);
      //printf("mag received\n");
      deltax.receive(&dx);
      //printf("dx received\n");
      deltay.receive(&dy);
      //printf("test nms before\n");
      non_max_supp(mag, dx, dy, ROWS, COLS, result);
      //printf("test nms after\n");
      imgOut.send(result);
   }
};

behavior Apply_Hysteresis(i_img_receiver nmsIn, i_calcsIm_receiver magIn, i_img_sender edgeOut) {

	/*******************************************************************************
	* PROCEDURE: follow_edges
	* PURPOSE: This procedure edges is a recursive routine that traces edgs along
	* all paths whose magnitude values remain above some specifyable lower
	* threshhold.
	* NAME: Mike Heath
	* DATE: 2/15/96
	*******************************************************************************/
	void follow_edges(unsigned char *edgemapptr, short *edgemagptr, short lowval,
	   int cols)
	{
	   short *tempmagptr;
	   unsigned char *tempmapptr;
	   int i;
	   int x[8] = {1,1,0,-1,-1,-1,0,1},
	       y[8] = {0,1,1,1,0,-1,-1,-1};

	   for(i=0;i<8;i++){
	      tempmapptr = edgemapptr - y[i]*cols + x[i];
	      tempmagptr = edgemagptr - y[i]*cols + x[i];

	      if((*tempmapptr == POSSIBLE_EDGE) && (*tempmagptr > lowval)){
		 *tempmapptr = (unsigned char) EDGE;
		 follow_edges(tempmapptr,tempmagptr, lowval, cols);
	      }
	   }
	}

	/*******************************************************************************
	* PROCEDURE: apply_hysteresis
	* PURPOSE: This routine finds edges that are above some high threshhold or
	* are connected to a high pixel by a path of pixels greater than a low
	* threshold.
	* NAME: Mike Heath
	* DATE: 2/15/96
	*******************************************************************************/
	void apply_hyst(short int *mag, unsigned char *nms, int rows, int cols,
		float tlow, float thigh, unsigned char *edge)
	{
	   int r, c, pos, numedges, highcount, lowthreshold, highthreshold, hist[32768];
	   short int maximum_mag;

	   /****************************************************************************
	   * Initialize the edge map to possible edges everywhere the non-maximal
	   * suppression suggested there could be an edge except for the border. At
	   * the border we say there can not be an edge because it makes the
	   * follow_edges algorithm more efficient to not worry about tracking an
	   * edge off the side of the image.
	   ****************************************************************************/
	   for(r=0,pos=0;r<rows;r++){
	      for(c=0;c<cols;c++,pos++){
		 if(nms[pos] == POSSIBLE_EDGE) edge[pos] = POSSIBLE_EDGE;
		 else edge[pos] = NOEDGE;
	      }
	   }

	   for(r=0,pos=0;r<rows;r++,pos+=cols){
	      edge[pos] = NOEDGE;
	      edge[pos+cols-1] = NOEDGE;
	   }
	   pos = (rows-1) * cols;
	   for(c=0;c<cols;c++,pos++){
	      edge[c] = NOEDGE;
	      edge[pos] = NOEDGE;
	   }

	   /****************************************************************************
	   * Compute the histogram of the magnitude image. Then use the histogram to
	   * compute hysteresis thresholds.
	   ****************************************************************************/
	   for(r=0;r<32768;r++) hist[r] = 0;
	   for(r=0,pos=0;r<rows;r++){
	      for(c=0;c<cols;c++,pos++){
		 if(edge[pos] == POSSIBLE_EDGE) hist[mag[pos]]++;
	      }
	   }

	   /****************************************************************************
	   * Compute the number of pixels that passed the nonmaximal suppression.
	   ****************************************************************************/
	   for(r=1,numedges=0;r<32768;r++){
	      if(hist[r] != 0) maximum_mag = r;
	      numedges += hist[r];
	   }

	   highcount = (int)(numedges * thigh + 0.5);

	   /****************************************************************************
	   * Compute the high threshold value as the (100 * thigh) percentage point
	   * in the magnitude of the gradient histogram of all the pixels that passes
	   * non-maximal suppression. Then calculate the low threshold as a fraction
	   * of the computed high threshold value. John Canny said in his paper
	   * "A Computational Approach to Edge Detection" that "The ratio of the
	   * high to low threshold in the implementation is in the range two or three
	   * to one." That means that in terms of this implementation, we should
	   * choose tlow ~= 0.5 or 0.33333.
	   ****************************************************************************/
	   r = 1;
	   numedges = hist[1];
	   while((r<(maximum_mag-1)) && (numedges < highcount)){
	      r++;
	      numedges += hist[r];
	   }
	   highthreshold = r;
	   lowthreshold = (int)(highthreshold * tlow + 0.5);

	   if(VERBOSE){
	      printf("The input low and high fractions of %f and %f computed to\n",
		 tlow, thigh);
	      printf("magnitude of the gradient threshold values of: %d %d\n",
		 lowthreshold, highthreshold);
	   }

	   /****************************************************************************
	   * This loop looks for pixels above the highthreshold to locate edges and
	   * then calls follow_edges to continue the edge.
	   ****************************************************************************/
	   for(r=0,pos=0;r<rows;r++){
	      for(c=0;c<cols;c++,pos++){
		 if((edge[pos] == POSSIBLE_EDGE) && (mag[pos] >= highthreshold)){
		    edge[pos] = EDGE;
		    follow_edges((edge+pos), (mag+pos), lowthreshold, cols);
		 }
	      }
	   }

	   /****************************************************************************
	   * Set all the remaining possible edges to non-edges.
	   ****************************************************************************/
	   for(r=0,pos=0;r<rows;r++){
	      for(c=0;c<cols;c++,pos++) if(edge[pos] != EDGE) edge[pos] = NOEDGE;
	   }
	}

   void main(void) {
      img nms, edge;
      calcsIm mag;
      //printf("test hyst\n");
      nmsIn.receive(&nms);
      magIn.receive(&mag);
      apply_hyst(mag, nms, ROWS, COLS, TLOW, THIGH, edge);
      edgeOut.send(edge);
   }
};

behavior Stimulus(i_img_sender frameOut) {
   img outImg;
      
   int read_pgm_img(const char *infilename, unsigned char *image, int rows, int cols)
   {
      FILE *fp;
      char buf[71];
      int r, c;

   /***************************************************************************
   * Open the input image file for reading if a filename was given. If no
   * filename was provided, set fp to read from standard input.
   ***************************************************************************/
      if(infilename == NULL) fp = stdin;
      else{
         if((fp = fopen(infilename, "r")) == NULL){
            fprintf(stderr, "Error reading the file %s in read_pgm_img().\n",
               infilename);
            return(0);
         }
      }

   /***************************************************************************
   * Verify that the image is in PGM format, read in the number of columns
   * and rows in the image and scan past all of the header information.
   ***************************************************************************/
      fgets(buf, 70, fp);
      if(strncmp(buf,"P5",2) != 0){
         fprintf(stderr, "The file %s is not in PGM format in ", infilename);
         fprintf(stderr, "read_pgm_img().\n");
         if(fp != stdin) fclose(fp);
         return(0);
      }
      do{ fgets(buf, 70, fp); }while(buf[0] == '#');  /* skip all comment lines */
      sscanf(buf, "%d %d", &c, &r);
      if(c != cols || r != rows){
         fprintf(stderr, "The file %s is not a %d by %d image in ", infilename, cols, rows);
         fprintf(stderr, "read_pgm_img().\n");
         if(fp != stdin) fclose(fp);
         return(0);
      }
      do{ fgets(buf, 70, fp); }while(buf[0] == '#');  /* skip all comment lines */

   /***************************************************************************
   * Read the image from the file.
   ***************************************************************************/
      if((unsigned)rows != fread(image, cols, rows, fp)){
         fprintf(stderr, "Error reading the image data in read_pgm_img().\n");
         if(fp != stdin) fclose(fp);
         return(0);
      }

      if(fp != stdin) fclose(fp);
      return(1);
   }
 
   void main(void) {
      unsigned char image[SIZE];
      char infilename[70];
      char inbuffer[70];
      int i;      
 
      for (i = 1; i < 21; i++) {
         if (i < 10){ 
      	   snprintf(inbuffer, 70, "video/EngPlaza00%d.pgm", i);
         }

         else {
           snprintf(inbuffer, 70, "video/EngPlaza0%d.pgm", i);
         }

         infilename = inbuffer;
         if(VERBOSE) printf("Reading the image %s.\n", infilename);
       
         if(read_pgm_img(infilename, image, ROWS, COLS) == 0){
            fprintf(stderr, "Error reading the input image, %s.\n", infilename);
      	    exit(1);
         }
         
         outImg = image;
         printf("Stimulus sent frame %d.\n", i);
         frameOut.send(outImg);
      }
   }
};

behavior DUT(i_img_receiver imIn, i_img_sender edOut) {      
   c_calcsIm_queue smoothimage(1ul), deltaX(1ul), deltaY(1ul), mag(1ul), deltaX2(1ul), deltaY2(1ul), mag2(1ul);
   c_img_queue result(1ul);

   Gaussian_Smooth gaussian_smooth(imIn, smoothimage);
   Derivative derivative(smoothimage, deltaX, deltaY, deltaX2, deltaY2);
   Magnitude magnitude(deltaX, deltaY, mag, mag2);
   NMS nms(mag, deltaX2, deltaY2, result);
   Apply_Hysteresis apply_hysteresis(result, mag2, edOut);

   void main(void)
   {
   /****************************************************************************
   * Perform the edge detection. All of the work takes place here.
   ****************************************************************************/
      while(true) { 
         gaussian_smooth.main(); 
         derivative.main(); 
	 magnitude.main(); 
         nms.main(); 
         apply_hysteresis.main(); 
     }
  }
};

behavior Platform(i_img_receiver Pin, i_img_sender Pout) {
   c_img_queue dinToC(1ul), CToDout(1ul);

   DataIn din(Pin, dinToC);
   DUT canny(dinToC, CToDout);
   DataOut dout(CToDout, Pout);

   void main(void)
   {
      par { din; canny; dout; }
   }
};

behavior Monitor(i_img_receiver frameIn) {
   img imgIn;
    
   int write_pgm_img(const char *outfilename, unsigned char *image, int rows, 
      int cols, const char *comment, int maxval)
   {
      FILE *fp;

   /***************************************************************************
   * Open the output image file for writing if a filename was given. If no
   * filename was provided, set fp to write to standard output.
   ***************************************************************************/
      if(outfilename == NULL) fp = stdout;
      else {
         if((fp = fopen(outfilename, "w")) == NULL){
            fprintf(stderr, "Error writing the file %s in write_pgm_img().\n",
               outfilename);
            return(0);
         }
      }

   /***************************************************************************
   * Write the header information to the PGM file.
   ***************************************************************************/
      fprintf(fp, "P5\n%d %d\n", cols, rows);
      if(comment != NULL)
         if(strlen(comment) <= 70) fprintf(fp, "# %s\n", comment);
      fprintf(fp, "%d\n", maxval);

   /***************************************************************************
   * Write the image data to the file.
   ***************************************************************************/
      if((unsigned)rows != fwrite(image, cols, rows, fp)){
         fprintf(stderr, "Error writing the image data in write_pgm_img().\n");
         if(fp != stdout) fclose(fp);
         return(0);
      }

      if(fp != stdout) fclose(fp);
      return(1);
   }

   void main(void) {
      char outbuffer[70];
      char outfilename[128];      
      int i;      

      for (i = 1; i < 21; i++) {
         if (i < 10){ 
           snprintf(outbuffer, 70, "EngPlaza00%d", i);
         }

         else {
           snprintf(outbuffer, 70, "EngPlaza0%d", i);
         }

         frameIn.receive(&imgIn);
	 printf("Monitor received frame %d.\n", i);

         sprintf(outfilename, "%s_edges.pgm", outbuffer);
         if(VERBOSE) printf("Writing the edge iname in the file %s.\n", outfilename);
         if(write_pgm_img(outfilename, imgIn, ROWS, COLS, "", 255) == 0){
            fprintf(stderr, "Error writing the edge image, %s.\n", outfilename);
            exit(1);
         }
      }
     sim_exit(0);
   }
};

behavior Main
{
   c_img_queue Cin(1ul), Cout(1ul);
    
   Stimulus stimulus(Cin);
   Platform dut(Cin, Cout);
   Monitor monitor(Cout);

   int main(void) {
      par { stimulus; dut; monitor; } 
      return 0;
   }
};
