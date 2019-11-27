package com.serenegiant.glutils;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2019 saki t_saki@serenegiant.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
*/

import android.annotation.SuppressLint;
import android.opengl.Matrix;
import android.util.Log;

import com.serenegiant.system.BuildCheck;
import com.serenegiant.utils.BufferHelper;

import java.nio.FloatBuffer;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;

import static com.serenegiant.glutils.ShaderConst.*;

/**
 * 描画領域全面にテクスチャを2D描画するためのヘルパークラス
 * 基本的に直接生成せずにGLDrawer2D#createメソッドを使うこと
 */
public abstract class GLDrawer2D implements IDrawer2D {
	private static final boolean DEBUG = false; // FIXME set false on release
	private static final String TAG = GLDrawer2D.class.getSimpleName();

	protected static final float[] DEFAULT_VERTICES = { 1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, -1.0f, -1.0f };
	protected static final float[] DEFAULT_TEXCOORD = { 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f };
	protected static final int FLOAT_SZ = Float.SIZE / 8;

	/**
	 * インスタンス生成のためのヘルパーメソッド
	 * 頂点シェーダーとフラグメントシェーダはデフォルトのものを使う
	 * @param isOES
	 * @return
	 */
	public static GLDrawer2D create(final boolean isGLES3, final boolean isOES) {
		return create(isGLES3, DEFAULT_VERTICES, DEFAULT_TEXCOORD, isOES);
	}

	/**
	 * インスタンス生成のためのヘルパーメソッド
	 * @param vertices
	 * @param texcoord
	 * @param isOES
	 * @return
	 */
	@SuppressLint("NewApi")
	public static GLDrawer2D create(final boolean isGLES3,
		@NonNull final float[] vertices,
		@NonNull final float[] texcoord, final boolean isOES) {

		if (isGLES3 && BuildCheck.isAndroid4_3()) {
			return new GLDrawer2DES3(vertices, texcoord, isOES);
		} else {
			return new GLDrawer2DES2(vertices, texcoord, isOES);
		}
	}

//================================================================================
	public final boolean isGLES3;
	protected final int VERTEX_NUM;
	protected final int VERTEX_SZ;
	protected final FloatBuffer pVertex;
	protected final FloatBuffer pTexCoord;
	protected final int mTexTarget;
	protected int hProgram;
	protected int maPositionLoc;
	protected int maTextureCoordLoc;
	protected int muMVPMatrixLoc;
	protected int muTexMatrixLoc;
    @NonNull
	protected final float[] mMvpMatrix = new float[16];
	private int errCnt;

	/**
	 * コンストラクタ
	 * GLコンテキスト/EGLレンダリングコンテキストが有効な状態で呼ばないとダメ
	 * @param vertices 頂点座標, floatを8個 = (x,y) x 4ペア
	 * @param texcoord テクスチャ座標, floatを8個 = (s,t) x 4ペア
	 * @param isOES 外部テクスチャ(GL_TEXTURE_EXTERNAL_OES)を描画に使う場合はtrue。
	 * 				通常の2Dテキスチャを描画に使うならfalse
	 */
	protected GLDrawer2D(final boolean isGLES3,
		final float[] vertices,
		final float[] texcoord, final boolean isOES) {

		this.isGLES3 = isGLES3;
		VERTEX_NUM = Math.min(
			vertices != null ? vertices.length : 0,
			texcoord != null ? texcoord.length : 0) / 2;
		VERTEX_SZ = VERTEX_NUM * 2;

		mTexTarget = isOES ? GL_TEXTURE_EXTERNAL_OES : GL_TEXTURE_2D;
		pVertex = BufferHelper.createFloatBuffer(vertices);
		pTexCoord = BufferHelper.createFloatBuffer(texcoord);

		// モデルビュー変換行列を初期化
		Matrix.setIdentityM(mMvpMatrix, 0);

		resetShader();
	}

	/**
	 * 破棄処理。GLコンテキスト/EGLレンダリングコンテキスト内で呼び出さないとダメ
	 * IDrawer2Dの実装
	 */
	@CallSuper
	@Override
	public void release() {
		releaseShader();
	}

	/**
	 * 外部テクスチャを使うかどうか
	 * IShaderDrawer2dの実装
	 * @return
	 */
	public boolean isOES() {
		return mTexTarget == GL_TEXTURE_EXTERNAL_OES;
	}

	/**
	 * モデルビュー変換行列を取得(内部配列を直接返すので変更時は要注意)
	 * IDrawer2Dの実装
	 * @return
	 */
	@Override
	public float[] getMvpMatrix() {
		return mMvpMatrix;
	}

	/**
	 * モデルビュー変換行列に行列を割り当てる
	 * IDrawer2Dの実装
	 * @param matrix 領域チェックしていないのでoffsetから16個以上必須
	 * @param offset
	 * @return
	 */
	@Override
	public IDrawer2D setMvpMatrix(final float[] matrix, final int offset) {
		System.arraycopy(matrix, offset, mMvpMatrix, 0, 16);
		return this;
	}

	/**
	 * モデルビュー変換行列のコピーを取得
	 * IDrawer2Dの実装
	 * @param matrix 領域チェックしていないのでoffsetから16個以上必須
	 * @param offset
	 */
	@Override
	public void copyMvpMatrix(final float[] matrix, final int offset) {
		System.arraycopy(mMvpMatrix, 0, matrix, offset, 16);
	}

	/**
	 * IGLSurfaceオブジェクトを描画するためのヘルパーメソッド
	 * IGLSurfaceオブジェクトで管理しているテクスチャ名とテクスチャ座標変換行列を使って描画する
	 * IDrawer2Dの実装
	 * @param surface
	 */
	@Override
	public void draw(@NonNull final IGLSurface surface) {
		draw(surface.getTexId(), surface.getTexMatrix(), 0);
	}

	@Override
	public synchronized void draw(final int texId,
		final float[] tex_matrix, final int offset) {

//		if (DEBUG) Log.v(TAG, "draw");
		if (hProgram < 0) return;
		glUseProgram();
		if (tex_matrix != null) {
			// テクスチャ変換行列が指定されている時
			updateTexMatrix(tex_matrix, offset);
		}
		// モデルビュー変換行列をセット
		updateMvpMatrix(mMvpMatrix);
		bindTexture(texId);
		if (true) {
			// XXX 共有コンテキストを使っていると頂点配列が壊れてしまうときがあるようなので都度読み込む
			updateVertex();
		}
		if (validateProgram(hProgram)) {
			drawArrays();
		} else {
			if (errCnt++ == 0) {
				Log.w(TAG, "draw:invalid program");
				// FIXME シェーダーを再初期化する?
			}
		}
		finishDraw();
	}

	protected abstract void updateTexMatrix(final float[] tex_matrix, final int offset);
	protected abstract void updateMvpMatrix(final float[] mvpMatrix);
	protected abstract void bindTexture(final int texId);
	protected abstract void updateVertex();
	protected abstract void drawArrays();
	protected abstract void finishDraw();

	/**
	 * テクスチャ名生成のヘルパーメソッド
	 * GLHelper#initTexを呼び出すだけ
	 * @return texture ID
	 */
	public abstract int initTex();

	/**
	 * テクスチャ名破棄のヘルパーメソッド
	 * GLHelper.deleteTexを呼び出すだけ
	 * @param hTex
	 */
	public abstract void deleteTex(final int hTex);

	/**
	 * 頂点シェーダー・フラグメントシェーダーを変更する
	 * GLコンテキスト/EGLレンダリングコンテキスト内で呼び出さないとダメ
	 * glUseProgramが呼ばれた状態で返る
	 * @param vs 頂点シェーダー文字列
	 * @param fs フラグメントシェーダー文字列
	 */
	public synchronized void updateShader(@NonNull final String vs, @NonNull final String fs) {
		releaseShader();
		hProgram = loadShader(vs, fs);
		init();
	}

	/**
	 * シェーダーを破棄
	 */
	protected void releaseShader() {
		if (hProgram >= 0) {
			internalReleaseShader(hProgram);
		}
		hProgram = -1;
	}

	protected abstract int loadShader(@NonNull final String vs, @NonNull final String fs);
	protected abstract void internalReleaseShader(final int program);

	/**
	 * フラグメントシェーダーを変更する
	 * GLコンテキスト/EGLレンダリングコンテキスト内で呼び出さないとダメ
	 * glUseProgramが呼ばれた状態で返る
	 * @param fs フラグメントシェーダー文字列
	 */
	public void updateShader(@NonNull final String fs) {
		updateShader(VERTEX_SHADER_ES2, fs);
	}

	/**
	 * 頂点シェーダー・フラグメントシェーダーをデフォルトに戻す
	 */
	public void resetShader() {
		releaseShader();
		if (isOES()) {
			hProgram = loadShader(VERTEX_SHADER_ES2, FRAGMENT_SHADER_EXT_SIMPLE_ES2);
		} else {
			hProgram = loadShader(VERTEX_SHADER_ES2, FRAGMENT_SHADER_SIMPLE_ES2);
		}
		init();
	}

	/**
	 * アトリビュート変数のロケーションを取得
	 * glUseProgramが呼ばれた状態で返る
	 * @param name
	 * @return
	 */
	public abstract int glGetAttribLocation(@NonNull final String name);

	/**
	 * ユニフォーム変数のロケーションを取得
	 * glUseProgramが呼ばれた状態で返る
	 * @param name
	 * @return
	 */
	public abstract int glGetUniformLocation(@NonNull final String name);

	/**
	 * glUseProgramが呼ばれた状態で返る
	 */
	public abstract void glUseProgram();

	/**
	 * シェーダープログラム変更時の初期化処理
	 * glUseProgramが呼ばれた状態で返る
	 */
	protected abstract void init();

	/**
	 * シェーダープログラムが使用可能かどうかをチェック
	 * @param program
	 * @return
	 */
	protected abstract boolean validateProgram(final int program);
}