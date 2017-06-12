package com.taehyen.videomediacodectranscodeexample;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

import wseemann.media.FFmpegMediaMetadataRetriever;

/**
 * Created by Taehyen on 2017-06-12.
 */

public class SoftInputSurfaceThread extends Thread {

    private static final String TAG = "SoftInputSurface";

    /*
    Encoder Spec 정의
     */
    private static final String MIME_TYPE = "video/avc";

    private static final int BIT_RATE = 16000000;
    private static final int IFRAME_INTERVAL = 10;

    private int width, height, frameRate, orientation;

    //decoder
    private static final String VIDEO = "video/";
    private MediaExtractor mExtractor;
    private MediaCodec mDecoder;
    private boolean eosReceived;

    //Encoder
    private MediaCodec.BufferInfo mBufferInfo;
    private MediaCodec mEncoder;
    private MediaMuxer mMuxer;
    private Surface mInputSurface;
    private int mTrackIndex;
    private boolean mMuxerStarted;

    private OnSoftInputSurfaceListener listener;


    public void init(Context context, String path, OnSoftInputSurfaceListener listener){
        try{
            this.listener = listener;

            FFmpegMediaMetadataRetriever fmmr = new FFmpegMediaMetadataRetriever();
            fmmr.setDataSource(context, Uri.parse(path));

            this.width = Integer.parseInt(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            this.height = Integer.parseInt(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            this.frameRate = (int)Double.parseDouble(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_FRAMERATE));
            this.orientation = Integer.parseInt(fmmr.extractMetadata(FFmpegMediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));

            fmmr.release();

            String dir = Environment.getExternalStorageDirectory().getAbsolutePath()+"/DCIM/SpringTest";
            File f = new File(dir);
            if(!f.exists()){
                f.mkdirs();
            }

            prepareEncoder(dir+"/outsurface1.mp4");
            prepareDecoder(mInputSurface, path);

        }catch (IOException e){
            e.printStackTrace();
        }
    }

    /**
     * Decoder 초기화 작업
     * @param surface 인코더에서 받은 서피스
     * @param inputFile 인풋 파일 경로
     * @return
     */
    private boolean prepareDecoder(Surface surface, String inputFile) {
        eosReceived = false;
        try {
            mExtractor = new MediaExtractor();
            mExtractor.setDataSource(inputFile);

            for (int i = 0; i < mExtractor.getTrackCount(); i++) {
                MediaFormat format = mExtractor.getTrackFormat(i);

                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith(VIDEO)) {
                    mExtractor.selectTrack(i);
                    mDecoder = MediaCodec.createDecoderByType(mime);
                    Log.d(TAG, "MIME: "+mime);
                    try {
                        Log.d(TAG, "format : " + format);

                        mDecoder.configure(format, surface, null, 0 /* Decoder */);

                    } catch (IllegalStateException e) {
                        Log.e(TAG, "codec '" + mime + "' failed configuration. " + e);
                        return false;
                    }

                    mDecoder.start();
                    break;
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }

    /**
     * Encoder 초기화 작업
     * @param outputFile 아웃풋 파일 경로
     * @throws IOException
     */
    private void prepareEncoder(String outputFile) throws IOException {
        mBufferInfo = new MediaCodec.BufferInfo();

        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

        mEncoder = MediaCodec.createEncoderByType(MIME_TYPE);
        mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mEncoder.createInputSurface();
        mEncoder.start();

        mMuxer = new MediaMuxer(outputFile,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mMuxer.setOrientationHint(orientation);
        mTrackIndex = -1;
        mMuxerStarted = false;

    }


    @Override
    public void run() {
        generateFrame();
    }

    /**
     * 디코드해서 나온 프레임
     */
    private void generateFrame() {
        long startTime = System.currentTimeMillis();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        ByteBuffer[] inputBuffers = mDecoder.getInputBuffers();

        boolean isInput = true;


        long presentationTimeUs = 0;

        while (!eosReceived) {
            if (isInput) {
                int inputIndex = mDecoder.dequeueInputBuffer(10000);
                if (inputIndex >= 0) {
                    // fill inputBuffers[inputBufferIndex] with valid data
                    ByteBuffer inputBuffer = inputBuffers[inputIndex];

                    int sampleSize = mExtractor.readSampleData(inputBuffer, 0);

                    if (mExtractor.advance() && sampleSize > 0) {
                        mDecoder.queueInputBuffer(inputIndex, 0, sampleSize, mExtractor.getSampleTime(), 0);

                    } else {
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");

                        mDecoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isInput = false;
                    }
                }
            }

            int outIndex = mDecoder.dequeueOutputBuffer(info, 10000);
            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    mDecoder.getOutputBuffers();
                    break;

                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_FORMAT_CHANGED format : " + mDecoder.getOutputFormat());
                    break;

                case MediaCodec.INFO_TRY_AGAIN_LATER:

                    break;

                default:

                    Log.d(TAG, "INFO.PRES: " + info.presentationTimeUs);
                    presentationTimeUs = info.presentationTimeUs;


                    mDecoder.releaseOutputBuffer(outIndex, true /* Surface init */);

                    //한 프레임 디코드 할 때 마다 한 프레임씩 인코드 한다
                    drainEncoder(false, presentationTimeUs);

                    break;
            }

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                drainEncoder(true, presentationTimeUs);
                break;
            }
        }


        mDecoder.stop();
        mDecoder.release();
        mExtractor.release();

        releaseEncoder();

        //선처리에 걸리는 시간 측정을 위해
        long endTime = System.currentTimeMillis();
        Log.d(TAG, "DECODE_TIME : " + (float) ((float) (endTime - startTime) / (float) 1000.0f));
    }

    /**
     * 한프레임 씩 인코딩 하는 역할
     * @param endOfStream 마지막 프레임인지? (true면 마지막 프레임)
     * @param presentationTimeUs 디코더에서 받은 프레임 타임스탬프
     */
    private void drainEncoder(boolean endOfStream, long presentationTimeUs) {
        final int TIMEOUT_USEC = 10000;
        if (endOfStream) {
            Log.d(TAG, "sending EOS to encoder");
            mEncoder.signalEndOfInputStream();
        }

        ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();

        while (true) {

            int encoderStatus = mEncoder.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
            Log.d(TAG, "ENCODER_STATUS: "+encoderStatus);
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {

                if (!endOfStream) {
                    break;      // out of while
                } else {
                    Log.d(TAG, "no output available, spinning to await EOS");

                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

                encoderOutputBuffers = mEncoder.getOutputBuffers();
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

                if (mMuxerStarted) {
                    throw new RuntimeException("format changed twice");
                }
                MediaFormat newFormat = mEncoder.getOutputFormat();
                Log.d(TAG, "encoder output format changed: " + newFormat);


                mTrackIndex = mMuxer.addTrack(newFormat);
                mMuxer.start();
                mMuxerStarted = true;
            } else if (encoderStatus < 0) {
                Log.d(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                        encoderStatus);

            } else {

                ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                if (encodedData == null) {
                    throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                            " was null");
                }

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {

                    Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                    mBufferInfo.size = 0;
                }

                if (mBufferInfo.size != 0) {
                    if (!mMuxerStarted) {
                        throw new RuntimeException("muxer hasn't started");
                    }

                    encodedData.position(mBufferInfo.offset);
                    encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                    if(presentationTimeUs >= 0){
                        mBufferInfo.presentationTimeUs = presentationTimeUs;
                        mMuxer.writeSampleData(mTrackIndex, encodedData, mBufferInfo);
                    }


                }
                Log.d(TAG, "OK!!!!");
                mEncoder.releaseOutputBuffer(encoderStatus, false);

                if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly");
                    } else {
                        Log.d(TAG, "end of stream reached");
                    }
                    break;      // out of while
                }

            }
        }
    }

    /**
     * Encoder 릴리즈
     */
    private void releaseEncoder() {

        if (mEncoder != null) {
            mEncoder.stop();
            mEncoder.release();
            mEncoder = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
        if (mMuxer != null) {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
        if(listener != null){
            listener.onFinishTransCoding();
        }
    }

    /**
     * 트랜스코딩의 끝을 알리는 리스너 인터페이스
     */
    public interface OnSoftInputSurfaceListener {
        /**
         * 트랜스코딩 완료해서 파일까지 나왔을 때 호출 시켜줌
         */
        void onFinishTransCoding();
    }
}
