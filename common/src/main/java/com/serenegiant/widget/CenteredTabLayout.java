package com.serenegiant.widget;
/*
 * libcommon
 * utility/helper classes for myself
 *
 * Copyright (c) 2014-2021 saki t_saki@serenegiant.com
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
import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.tabs.TabLayout;

import java.util.Locale;

import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;

/**
 * 選択中のタブを中央に表示＆左右フリックとタブへのタッチで
 * 選択中のタブを切り替えることができるTabLayout
 */
public class CenteredTabLayout extends TabLayout {
	private static final boolean DEBUG = false;	// XXX set false on production
	private static final String TAG = CenteredTabLayout.class.getSimpleName();

	private final GestureDetectorCompat mGestureDetector;
	private boolean mIsLocked = false;

	public CenteredTabLayout(final Context context) {
		this(context, null, 0);
	}

	public CenteredTabLayout(final Context context, final AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public CenteredTabLayout(final Context context, final AttributeSet attrs, final int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		super.setTabMode(TabLayout.MODE_SCROLLABLE);	// MODE_FIXEDだと中央に表示や手動選択できない
		mGestureDetector = new GestureDetectorCompat(context,
			new GestureDetector.SimpleOnGestureListener() {
				@Override
				public boolean onFling(final MotionEvent e1, final MotionEvent e2,
					final float velocityX, final float velocityY) {

					if (!mIsLocked) {
						final int current = getSelectedTabPosition();
						if (DEBUG) Log.v(TAG, String.format(Locale.US, "onFling:(%f,%f),cur=%d",
							velocityX, velocityY, current));

						if (velocityX < 0) {
							// 左へフリック==可能なら1つ右のタブを選択
							final int lastIx = getTabCount() - 1;
							if (current < lastIx) {
								post(new Runnable() {
									@Override
									public void run() {
										selectTab(getTabAt(current + 1), true);
									}
								});
							}
						} else {
							// 右へフリック==可能なら1つ左のタブを選択
							if (current > 0) {
								post(new Runnable() {
									@Override
									public void run() {
										selectTab(getTabAt(current - 1), true);
									}
								});
							}
						}
					}
					return false;
				}
			});
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		// TabLayoutはHorizontalScrollViewの子クラスなのでスクロール領域用のViewGroupを１つ含んでいる
		// スクロール領域用のViewGroupを取得
		final ViewGroup children = ((ViewGroup) getChildAt(0));
		if (getTabMode() == TabLayout.MODE_SCROLLABLE) {
			// 選択中のタブが中央に表示されるようにパディングを調整する
			// width=match_parent(左右一番端っこのタブを選択したときでもパディング設定できるだけの十分な幅)が必要
			// タブを選択したときには中央に表示されるけど、手動でスクロールしたときに選択状態が変わらないまま横にスクロールしてしまう

			// 先頭のタブViewを取得
			final View firstTab = children.getChildAt(0);
			// 最後のタブViewを取得
			final View lastTab = children.getChildAt(children.getChildCount() - 1);
			// パディングを調整
			ViewCompat.setPaddingRelative(children,
				(getWidth() / 2) - (firstTab.getWidth() / 2),
				0,
				(getWidth() / 2) - (lastTab.getWidth() / 2),
				0
			);
		} else {
			// ここのパディングがあってるかどうか未検証
			ViewCompat.setPaddingRelative(children,
				0,
				0,
				0,
				0
			);
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	@Override
	public boolean onTouchEvent(final MotionEvent event) {
//		if (DEBUG) Log.v(TAG, "onTouchEvent:" + event);
		mGestureDetector.onTouchEvent(event);
		// TabLayout/HorizontalScrollViewが自動的にスクロールするのを防ぐために常にtrueを返す
		return true;
	}

	@Override
	public void selectTab(@Nullable final Tab tab, boolean updateIndicator) {
		if (DEBUG) Log.v(TAG, "selectTab:lock=" + mIsLocked + ",tab=" + tab);
		final Tab current = getSelectedTab();
		if (!mIsLocked || ((tab != null) && tab.equals(current))) {
			// ロック中でないか同じタブを選択した時
			super.selectTab(tab, updateIndicator);
		}
	}

	/**
	 * 選択中のTabを取得する
	 * @return
	 */
	@Nullable
	public Tab getSelectedTab() {
		return getTabAt(getSelectedTabPosition());
	}

	/**
	 * Tabの選択変更・スクロールをロックするかどうかをセット
	 * @param lock
	 */
	public void setLock(final boolean lock) {
		if (DEBUG) Log.v(TAG, "setLock:" + lock);
		mIsLocked = lock;
		postInvalidate();
	}

	/**
	 * Tabの選択変更・スクロールロック中かどうかを取得
	 * @return
	 */
	public boolean getLock() {
		return mIsLocked;
	}

	@Override
	public void setTabMode(final int mode) {
		throw new UnsupportedOperationException("CenteredTabLayout does not support #setTabMode.");
	}
}
