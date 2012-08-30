package jp.ac.hosei.cis.k.matsumoto.lowmediaaudio;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class MainActivity extends Activity {
	private class LowMediaAudioPlayer {
		private Thread inputThread;
		private boolean inputThreadIsContinue = true;
		private Thread outputThread;
		private boolean outputThreadIsContinue = true;
		private MediaCodec codec;

		public void play(final MediaExtractor extractor, final String mime,
				final MediaFormat format) {

			inputThreadIsContinue = true;
			outputThreadIsContinue = true;
			codec = MediaCodec.createDecoderByType(mime);
			codec.configure(format, null, null, 0);
			codec.start();

			inputThread = new Thread(new Runnable() {

				@Override
				public void run() {

					boolean sawInputEOS = false;

					ByteBuffer[] inputBuffers = codec.getInputBuffers();

					// ���߂̃v���C�I���e�B��ݒ�
					Thread.currentThread().setPriority(Thread.MAX_PRIORITY - 1);

					while (inputThreadIsContinue) {
						// InputBuffer�����o��
						final int inputBufIndex = codec
								.dequeueInputBuffer(1000 * 1000);

						// InputBuffer�����o������
						if (inputBufIndex >= 0) {
							// ����g��ByteBuffer���擾
							final ByteBuffer dstBuf = inputBuffers[inputBufIndex];

							// Extractor����T���v���f�[�^�i���k�ς�)���擾
							int sampleSize = extractor
									.readSampleData(dstBuf, 0);

							long presentationTimeUs = 0;
							if (sampleSize < 0) {
								// �T���v���f�[�^�i���k�ς݁j�����Ȃ�������EOS�Ɣ���
								sawInputEOS = true;
								sampleSize = 0;
								Log.i("playAudio", "may be EOS");

								break;
							} else {
								// Extractor����T���v���f�[�^���\�����Ԃ��擾
								presentationTimeUs = extractor.getSampleTime();
								// Log.i("playAudio",
								// "presentationTimeUs / 1000 = "
								// + presentationTimeUs / 1000);
							}

							// �擾���I�����InputBuffer��Codec�֕ԋp
							codec.queueInputBuffer(
									inputBufIndex,
									0, // offset
									sampleSize,
									presentationTimeUs,
									sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM
											: 0);
							// EOS�ɓ��B���Ă��Ȃ�������A���̃T���v���f�[�^�i���k�ς݁j�Ɉړ�����
							if (!sawInputEOS) {
								extractor.advance();
							}

						} else {
							Log.i("playAudio", "fail to get inputbuffer");
							try {
								Thread.sleep(16);
							} catch (InterruptedException e) {
								throw new RuntimeException(e);
							}

						}
					}

				}
			});

			outputThread = new Thread(new Runnable() {

				@Override
				public void run() {
					ByteBuffer[] outputBuffers = null;
					AudioTrack audioTrack = null;
					MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
					boolean isPlayed = false;
					byte[] buffer = null;

					// ���߂̃v���C�I���e�B��ݒ�
					Thread.currentThread().setPriority(Thread.MAX_PRIORITY - 1);

					while (outputThreadIsContinue) {

						// �f�R�[�h���ʂ��i�[���ꂽBuffer��Index���擾
						final int res = codec.dequeueOutputBuffer(bufferInfo,
								1000);
						if (res >= 0) {
							int outputBufIndex = res;
							ByteBuffer buf = outputBuffers[outputBufIndex];

							if (buffer == null
									|| buffer.length < bufferInfo.size) {
								buffer = new byte[bufferInfo.size];
							}
							buf.position(bufferInfo.offset);
							buf.get(buffer, 0, bufferInfo.size);

							if (bufferInfo.size > 0) {
								int remaining = bufferInfo.size;
								int written = 0, written_once;
								for (;;) {
									written_once = audioTrack.write(buffer,
											written, remaining);
									written += written_once;
									remaining -= written_once;

									if (!isPlayed
											&& (remaining == 0 || written_once == 0)) {
										isPlayed = true;
										audioTrack.play();

									}
									if (remaining == 0)
										break;

									try {
										Thread.sleep(16);
									} catch (InterruptedException e) {
										throw new RuntimeException(e);
									}

								}

							}
							codec.releaseOutputBuffer(outputBufIndex, false);

							if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
								break;
							}
							buf.clear();
						} else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {

							outputBuffers = codec.getOutputBuffers();
						} else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {

							final int channelCount = format
									.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
							final int sampleRate = format
									.getInteger(MediaFormat.KEY_SAMPLE_RATE);

							final int channelConfig;
							if (channelCount == 1)
								channelConfig = AudioFormat.CHANNEL_OUT_MONO;
							else if (channelCount == 2)
								channelConfig = AudioFormat.CHANNEL_OUT_STEREO;
							else
								throw new RuntimeException(
										"Unrecoganized channel number.");

							// �擾������@��������Ȃ������̂Ō��ߑł�
							final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

							// �o�b�t�@�E�T�C�Y������
							final int bufferSize = AudioTrack.getMinBufferSize(
									sampleRate, channelConfig, audioFormat) * 2;
							audioTrack = new AudioTrack(
									AudioManager.STREAM_MUSIC, sampleRate,
									channelConfig, audioFormat, bufferSize,
									AudioTrack.MODE_STREAM);

						}

					}

					audioTrack.stop();
				}
			});

			inputThread.start();
			outputThread.start();
		}

		private void stop() {
			if (inputThread == null || outputThread == null)
				return;
			inputThreadIsContinue = false;
			outputThreadIsContinue = false;
			try {

				inputThread.join();
				outputThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			inputThread = null;
			outputThread = null;
			codec.stop();
			codec.release();
		}
	}

	private LowMediaAudioPlayer lowMediaAudioPlayer = new LowMediaAudioPlayer();

	private String analyzeAndPlay(final String path) {
		StringBuffer ret = new StringBuffer();

		MediaExtractor extractor = new MediaExtractor();
		extractor.setDataSource(path);
		ret.append("path : " + path + "\n");

		final int trackCount = extractor.getTrackCount();
		ret.append("track count : " + trackCount + "\n");

		List<MediaFormat> trackMediaFormatList = new ArrayList<MediaFormat>();
		for (int i = 0; i < trackCount; i++) {
			final MediaFormat format = extractor.getTrackFormat(i);
			final String mime = format.getString(MediaFormat.KEY_MIME);
			trackMediaFormatList.add(format);

			ret.append("MIME : " + mime + "\n");
			final long duration = format.getLong(MediaFormat.KEY_DURATION);
			ret.append("\tDuration : " + duration + "\n");

			if (mime.startsWith("video/")) {
				final int height = format.getInteger(MediaFormat.KEY_HEIGHT);
				final int width = format.getInteger(MediaFormat.KEY_WIDTH);

				ret.append("\tHeight : " + height + "\n");
				ret.append("\tWidth : " + width + "\n");

			} else if (mime.startsWith("audio/")) {
				final int channelCount = format
						.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
				final int sampleRate = format
						.getInteger(MediaFormat.KEY_SAMPLE_RATE);
				// ����͂������A�Ȃ������Ȃ��E�E�E�B
				// final int channelMask = format
				// .getInteger(MediaFormat.KEY_CHANNEL_MASK);

				ret.append("\tChannel Count : " + channelCount + "\n");
				ret.append("\tSample Rate : " + sampleRate + "\n");
				// ret.append("\tChannel Mask : " + channelMask + "\n");

				extractor.selectTrack(i);
				lowMediaAudioPlayer.play(extractor, mime, format);

				// �P�g���b�N�݂̂̍Đ��ɑΉ�
				break;
			} else {
			}

		}
		return ret.toString();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		//
		((EditText) findViewById(R.id.mediaPathEditText))
				.setText("/sdcard/lion.mp3");
		//
		((Button) findViewById(R.id.analyzeAndPlayButton))
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						final String mediaPath = ((EditText) findViewById(R.id.mediaPathEditText))
								.getText().toString();

						((EditText) findViewById(R.id.resultEditText))
								.setText(analyzeAndPlay(mediaPath));
					}
				});

		((Button) findViewById(R.id.stopButton))
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						lowMediaAudioPlayer.stop();
					}
				});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

}
