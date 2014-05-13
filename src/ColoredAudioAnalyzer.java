import processing.core.*;
import ddf.minim.analysis.*;
import ddf.minim.*;
 

/* Stallion res: 40,960x8000
 * h = 5.12 * w
 */
public class ColoredAudioAnalyzer extends PApplet {
	Minim minim;
	AudioInput in;
	FFT fft;
	float[] peaks;

	boolean paused = false;
	boolean drawScale = true;
	
	int peak_hold_time = 10;  // how long before peak decays
	int[] peak_age;  // tracks how long peak has been stable, before decaying

	// how wide each 'peak' band is, in fft bins
	int binsperband = 100;
	int peaksize; // how many individual peak bands we have (dep. binsperband)
	float gain = 40; // in dB
	float dB_scale = 2.0f;  // pixels per dB

	int buffer_size = 1024;  // also sets FFT size (frequency resolution)
	float sample_rate = 11025;

	int spectrum_height = 200; // determines range of dB shown
	int legend_height = 20;
	int spectrum_width = 560; //need to figure out what determines this number determines how much of spectrum we see
	int legend_width = 40;

	PGraphics canvas;
	
	Particle[] particles;
	boolean fade = true;
	float[] saved_freqs = new float[spectrum_width];
	float[] saved_buffer = new float[buffer_size];
	
	int loudest_freq = 0;
	int loudest_db = -1000;
	
	public void setup()
	{
	  int window_height = 220;
	  size((int) (window_height*5.12f), 220);
	  canvas = createGraphics((int) (window_height*5.12f)/2, 220, JAVA2D);
	  
	  textMode(SCREEN);
	  textFont(createFont("SanSerif", 12));
	 
	  minim = new Minim(this);
	 
	  in = minim.getLineIn(Minim.MONO,buffer_size,sample_rate);
	 
	  // create an FFT object that has a time-domain buffer 
	  // the same size as line-in's sample buffer
	  fft = new FFT(in.bufferSize(), in.sampleRate());
	  // Tapered window important for log-domain display
	  fft.window(FFT.HAMMING);

	  // initialize peak-hold structures
	  peaksize = 1+Math.round(fft.specSize()/binsperband);
	  peaks = new float[peaksize];
	  peak_age = new int[peaksize];
	  
	  particles = new Particle[ fft.specSize() ];
		for (int i = 0; i < fft.specSize(); i++)
			particles[i] = new Particle(i);
	  }

	int fill_amount = 70;
	public void draw()
	{
	  canvas.beginDraw();
	  if (paused) {
		  drawSavedValues();
		  return;
	  }
	  // clear window
	  //background(0);
	  fill(0, fill_amount); // semi-transparent white
	  rect(0, 0, width, height);
	  fill(random(255));
	  // perform a forward FFT on the samples in input buffer
	  fft.forward(in.mix);
	  loudest_freq = 0;
	  loudest_db = -1000;

	  // now draw current spectrum
	  for(int i = 0; i < spectrum_width; i++)  {
	    // draw the line for frequency band i using dB scale
	    float val = dB_scale*(20*((float)Math.log10(fft.getBand(i))) + gain);
	    if (fft.getBand(i) == 0) {   val = -200;   }  // avoid log(0)
	    int y = spectrum_height - Math.round(val);
	    y = y > spectrum_height ? spectrum_height : y;

	    if (i > 20 && val > loudest_db) {
	    	loudest_db = (int)val;
	    	loudest_freq = i;
	    }

	    int[] c = calculated_color(i);
	    stroke(c[0], c[1], c[2]);
	    noFill();
	    int x1 = (int) map(legend_width+i, legend_width, legend_width+spectrum_width, legend_width, width);
	    //int x1 = legend_width + i; 
	    int x2 = x1 + 1;
	    if (i < 520){
		    line(x1, spectrum_height, x1, y);
		    line(x2, spectrum_height, x2, y);
	    }
	    saved_freqs[i] = y;
	  }
	  
	  int[] color_waveform = calculated_color(loudest_freq);
	  println("Peak: " + loudest_freq + 
			  " dB: " + loudest_db + 
			  " Color " + color_waveform[0] + " " + color_waveform[1] + " " + color_waveform[2]);
	  stroke(color_waveform[0], color_waveform[1], color_waveform[2]);
	  drawWaveForm();
	  
	  drawParticles();
	  
	  // level axis
	  if (drawScale) {
		  // add legend
		  // frequency axis
		  fill(255);
		  stroke(255);
		  int y = spectrum_height;
		  //line(legend_width,y,legend_width+spectrum_width,y); // horizontal line
		  // x,y address of text is immediately to the left of the middle of the letters 
		  textAlign(CENTER,TOP);
		  for (float freq = 0.0f; freq < in.sampleRate()/2; freq += 2000.0) {
		    int x = legend_width+(fft.freqToIndex(freq)*2); // which bin holds this frequency
		    
		    line(x,y,x,y+4); // tick mark
		    text(Math.round(freq/1000) +"kHz", x, y+5); // add text label
		  }
	  
	  
		  int x = legend_width;
		  line(x,0,x,spectrum_height); // vertictal line
		  textAlign(RIGHT,CENTER);
		  for (float level = -100.0f; level < 100.0; level += 20.0) {
		    y = spectrum_height - (int)(dB_scale * (level+gain));
		    line(x,y,x-3,y);
		    text((int)level+" dB",x-5,y);
		  }
	  }
	  drawArcs();
	  canvas.endDraw();
	  image(canvas, 0, 0, width, height); // draw canvas streched to sketch dimensions			
	}

	private void drawSavedValues() {
		background(0);
		// now draw current spectrum
		  for(int i = 0; i < saved_freqs.length; i++)  {
			int[] c = calculated_color(i);
			stroke(c[0], c[1], c[2]);
			noFill();
			int x1 = (int) map(legend_width+i, legend_width, legend_width+spectrum_width, legend_width, width);
		    line(x1, spectrum_height, x1, saved_freqs[i]);
		    int x2 = x1 + 1;
		    line(x2, spectrum_height, x2, saved_freqs[i]);
		  }
		  
		  int[] color_waveform = calculated_color(loudest_freq);
		  stroke(color_waveform[0], color_waveform[1], color_waveform[2]);
		  for(int i = 0; i < saved_buffer.length - 1; i++)
		  {
		 	    line(i+50, height/2 + saved_buffer[i]*50, i+51, height/2 + saved_buffer[i]*50);
		  }
		  
		  drawParticles();
		  
		  if (drawScale) {
			  // add legend
			  // frequency axis
			  fill(255);
			  stroke(255);
			  int y = spectrum_height;
			  line(legend_width,y,legend_width+spectrum_width,y); // horizontal line
			  // x,y address of text is immediately to the left of the middle of the letters 
			  textAlign(CENTER,TOP);
			  for (float freq = 0.0f; freq < in.sampleRate()/2; freq += 2000.0) {
			    int x = legend_width+(fft.freqToIndex(freq)*2); // which bin holds this frequency
			    line(x,y,x,y+4); // tick mark
			    text(Math.round(freq/1000) +"kHz", x, y+5); // add text label
			  }
		  
		  // level axis
		  
			  int x = legend_width;
			  line(x,0,x,spectrum_height); // vertictal line
			  textAlign(RIGHT,CENTER);
			  for (float level = -100.0f; level < 100.0; level += 20.0) {
			    y = spectrum_height - (int)(dB_scale * (level+gain));
			    line(x,y,x-3,y);
			    text((int)level+" dB",x-5,y);
			  }
		  }  
		  
		  
		  
		  canvas.endDraw();
		  image(canvas, 0, 0, width, height); // draw canvas streched to sketch dimensions			
	}
	
	int lastX;
	int lastY;
	
	public void drawArcs(){
		int centerX = width/2;
		int centerY = height/2;
		int radiusMin = 50;
		for (int r = 4000; r < 360; r++) {
			r++; 
			float px = width/2  + cos(r)* 50;
			float py = height/2  + sin(r)* 50;
			ellipse(px, py, 5, 5);
		}
	}


	private void drawParticles() {
		pushStyle();
		colorMode(RGB, 255);
	    //background(0);
		popStyle();
		for (int i = 0; i < fft.specSize() - 1; i++) {
			float val = dB_scale*(20*((float)Math.log10(fft.getBand(i))));
		    if (fft.getBand(i) == 0) {   val = -200;   }  // avoid log(0)
			particles[i].update(val);
			particles[i].render();
		}
	}

	class Particle{
		PVector loc;
		PVector vel;
		float radius, h, s, b;
		int id;
		int[] c;
		
		Particle(int _id) {
			this.id = _id;
			loc = new PVector(map(_id, 0, fft.specSize(), 0 , width), height/2);
			vel = new PVector(random(-1, 1), random( -1, 1));
			c = calculated_color(_id);
		}
		
		public void update(float db) {
			// we can use the db value here to change the speed of the particles
			loc.add(vel.mult(vel, map(db, -100, 100, .05f, 5)));
			if (loc.x < 0 || loc.x > width) {
				vel.x *= -1;
			}
			if (loc.y < 0 || loc.y > height) {
				vel.y *= -1;
			}

			radius = constrain(db, 2, 100);
		}
		
		public void render() {
			stroke(c[0], c[1], c[2], 50);
			fill(c[0], c[1], c[2], 20);
			ellipse(loc.x, loc.y, radius, radius);
		}
	}	

	private void drawWaveForm() {
		for(int i = 0; i < in.bufferSize() - 1; i++)
		{
			saved_buffer[i] = in.left.get(i);
		    line(i+50, height/2 + in.left.get(i)*50, i+51, height/2 + in.left.get(i+1)*50);
		}
	}


	public void keyReleased()
	{
	  // +/- used to adjust gain on the fly
	  if (key == '+' || key == '=') {
	    gain = gain + 5.0f;
	  } else if (key == '-' || key == '_') {
	    gain = gain - 5.0f;
	  } else if (key == 'p') {
		  paused = !paused;
	  } else if (key == 'f') {
		  fill_amount = fill_amount == 70 ? 100 : 70;
	  } else if (key == 's') {
		  drawScale= !drawScale;
	  }
		
	}
	 
	public void stop()
	{
	  in.close();
	  minim.stop();
	  super.stop();
	}

	/* If X falls between A and B, and you would like Y to fall between C and D
	 * Y = (X-A)/(B-A) * (D-C) + C
	 */
	float Cminus4 = 1.02196875f;
	float Cminus3 = 2.0439375f;
	float Cminus2 = 4.087875f;
	float Cminus1 = 8.17575f;
	float C0 = 16.3515f;
	float C1 = 32.703f;
	float C2 = C1*2;
	float C3 = C2*2;
	float C4 = C3*2;
	float C5 = C4*2;
	float C6 = C5*2;
	float C7 = C6*2;
	float C8 = C7*2;
	public int[] calculated_color(int freq) {
		//println("Freq: " + freq);
		
	    //int green = spectrum_width - diff;
	    float min = 0;
	    float max = C1;
		if (freq > C1 && freq < C2) {
		    min = C1;
		    max = C2;
		}
		else if (freq >= C2 && freq < C3) {
		    min = C2;
		    max = C3;
		} 
		else if (freq >= C3 && freq < C4) {
		    min = C3;
		    max = C4;
		} 
		else if (freq >= C4 && freq < C5) {
		    min = C4;
		    max = C5;
		} 
		else if (freq >= C5 && freq < C6) {
		    min = C5;
		    max = C6;
		} 
		else if (freq >= C6 && freq < C7) {
		    min = C6;
		    max = C7;
		} 
		else if (freq >= C7 && freq < C8) {
		    min = C7;
		    max = C8;
		}
		//println("Min: " + min + " Max: " + max);
		// Y = (X-A)/(B-A) * (D-C) + C
		int red = (int)freq;
	    red = (int) map(red, (int)min, (int)max, 0, 255);
	    int blue = 255 - red;
	    int diff = abs(red - blue);
	    int green = 255 - diff;
	    //println("RGB (after): " + red + " " + green + " " + blue);
		int[] c = {red, green, blue};
	    return c;
	}
	
}