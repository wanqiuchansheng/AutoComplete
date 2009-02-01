/*
 * 12/21/2008
 *
 * AutoCompletePopupWindow.java - A window containing a list of auto-complete
 * choices.
 * Copyright (C) 2008 Robert Futrell
 * robert_futrell at users.sourceforge.net
 * http://fifesoft.com/rsyntaxtextarea
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA.
 */
package org.fife.ui.autocomplete;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JWindow;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.text.Caret;
import javax.swing.text.JTextComponent;


/**
 * The actual popup window of choices.  When visible, this window intercepts
 * certain keystrokes in the parent text component and uses them to navigate
 * the completion choices instead.  If Enter or Escape is pressed, the window
 * hides itself and notifies the {@link AutoCompletion} to insert the selected
 * text.
 *
 * @author Robert Futrell
 * @version 1.0
 */
class AutoCompletePopupWindow extends JWindow implements CaretListener,
									ListSelectionListener, MouseListener {

	/**
	 * The parent AutoCompletion instance.
	 */
	private AutoCompletion ac;

	/**
	 * The list of completion choices.
	 */
	private JList list;

	/**
	 * The contents of {@link #list()}.
	 */
	private CompletionListModel model;

	/**
	 * Optional popup window containing a description of the currently
	 * selected completion.
	 */
	private AutoCompleteDescWindow descWindow;

	/**
	 * The preferred size of the optional description window.  This field
	 * only exists because the user may (and usually will) set the size of
	 * the description window before it exists (it must be parented to a
	 * Window).
	 */
	private Dimension preferredDescWindowSize;

	/**
	 * Whether the completion window and the optional description window
	 * should be displayed above the current caret position (as opposed to
	 * underneath it, which is preferred unless there is not enough space).
	 */
	private boolean aboveCaret;

	private int lastLine;

	private KeyActionPair escapeKap;
	private KeyActionPair upKap;
	private KeyActionPair downKap;
	private KeyActionPair leftKap;
	private KeyActionPair rightKap;
	private KeyActionPair enterKap;
	private KeyActionPair tabKap;
	private KeyActionPair homeKap;
	private KeyActionPair endKap;
	private KeyActionPair pageUpKap;
	private KeyActionPair pageDownKap;
	private KeyActionPair ctrlCKap;

	private KeyActionPair oldEscape, oldUp, oldDown, oldLeft, oldRight,
			oldEnter, oldTab, oldHome, oldEnd, oldPageUp, oldPageDown,
			oldCtrlC;


	/**
	 * Constructor.
	 *
	 * @param parent The parent window (hosting the text component).
	 * @param ac The auto-completion instance.
	 */
	public AutoCompletePopupWindow(Window parent, AutoCompletion ac) {

		super(parent);

		this.ac = ac;
		model = new CompletionListModel();//DefaultListModel();
		list = new JList(model);
		list.setCellRenderer(new DelegatingCellRenderer());
		list.addListSelectionListener(this);
		list.addMouseListener(this);

		JPanel contentPane = new JPanel(new BorderLayout());
		JScrollPane sp = new JScrollPane(list,
							JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
							JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);

		// In 1.4, JScrollPane.setCorner() has a bug where it won't accept
		// JScrollPane.LOWER_TRAILING_CORNER, even though that constant is
		// defined.  So we have to put the logic added in 1.5 to handle it
		// here.
		JPanel corner = new SizeGrip();
		//sp.setCorner(JScrollPane.LOWER_TRAILING_CORNER, corner);
		boolean isLeftToRight = getComponentOrientation().isLeftToRight();
	    String str = isLeftToRight ? JScrollPane.LOWER_RIGHT_CORNER :
	    								JScrollPane.LOWER_LEFT_CORNER;
	    sp.setCorner(str, corner);

		contentPane.add(sp);
		setContentPane(contentPane);
		pack();

		setFocusableWindowState(false);

		lastLine = -1;

	}


	public void caretUpdate(CaretEvent e) {
		if (isVisible()) { // Should always be true
			int line = ac.getLineOfCaret();
			if (line!=lastLine) {
				lastLine = -1;
				setVisible(false);
			}
			else {
				doAutocomplete();
			}
		}
		else if (AutoCompletion.getDebug()) {
			Thread.dumpStack();
		}
	}


	/**
	 * Creates the description window.
	 *
	 * @return The description window.
	 */
	private AutoCompleteDescWindow createDescriptionWindow() {
		AutoCompleteDescWindow dw = new AutoCompleteDescWindow(this, ac);
		Dimension size = preferredDescWindowSize;
		if (size==null) {
			size = getSize();
		}
		dw.setSize(size);
		return dw;
	}


	/**
	 * Creates the mappings from keys to Actions we'll be putting into the
	 * text component's ActionMap and InputMap.
	 */
	private void createKeyActionPairs() {

		// Actions we'll install.
		EnterAction enterAction = new EnterAction();
		escapeKap = new KeyActionPair("Escape", new EscapeAction());
		upKap = new KeyActionPair("Up", new UpAction());
		downKap = new KeyActionPair("Down", new DownAction());
		leftKap = new KeyActionPair("Left", new LeftAction());
		rightKap = new KeyActionPair("Right", new RightAction());
		enterKap = new KeyActionPair("Enter", enterAction);
		tabKap = new KeyActionPair("Tab", enterAction);
		homeKap = new KeyActionPair("Home", new HomeAction());
		endKap = new KeyActionPair("End", new EndAction());
		pageUpKap = new KeyActionPair("PageUp", new PageUpAction());
		pageDownKap = new KeyActionPair("PageDown", new PageDownAction());
		ctrlCKap = new KeyActionPair("CtrlC", new CopyAction());

		// Buffers for the actions we replace.
		oldEscape = new KeyActionPair();
		oldUp = new KeyActionPair();
		oldDown = new KeyActionPair();
		oldLeft = new KeyActionPair();
		oldRight = new KeyActionPair();
		oldEnter = new KeyActionPair();
		oldTab = new KeyActionPair();
		oldHome = new KeyActionPair();
		oldEnd = new KeyActionPair();
		oldPageUp = new KeyActionPair();
		oldPageDown = new KeyActionPair();
		oldCtrlC = new KeyActionPair();

	}


	protected void doAutocomplete() {
		lastLine = ac.refreshPopupWindow();
	}


	/**
	 * Returns the default list cell renderer used when a completion provider
	 * does not supply its own.
	 *
	 * @return The default list cell renderer.
	 * @see #setListCellRenderer(ListCellRenderer)
	 */
	public ListCellRenderer getListCellRenderer() {
		DelegatingCellRenderer dcr = (DelegatingCellRenderer)list.
															getCellRenderer();
		return dcr.getFallbackCellRenderer();
	}


	/**
	 * Returns the selected value, or <code>null</code> if nothing is selected.
	 *
	 * @return The selected value.
	 */
	public Completion getSelection() {
		return (Completion)list.getSelectedValue();
	}


	/**
	 * Inserts the currently selected completion.
	 *
	 * @see #getSelection()
	 */
	public void insertSelectedCompletion() {
		Completion comp = getSelection();
		ac.insertCompletion(comp);
	}


	/**
	 * Registers keyboard actions to listen for in the text component and
	 * intercept.
	 *
	 * @see #uninstallKeyBindings()
	 */
	private void installKeyBindings() {

		if (AutoCompletion.getDebug()) {
			System.out.println("PopupWindow: Installing keybindings");
		}

		if (escapeKap==null) { // Lazily create actions.
			createKeyActionPairs();
		}

		JTextComponent comp = ac.getTextComponent();
		InputMap im = comp.getInputMap();
		ActionMap am = comp.getActionMap();

		replaceAction(im, am, KeyEvent.VK_ESCAPE, escapeKap, oldEscape);
		if (AutoCompletion.getDebug() && oldEscape.action==escapeKap.action) {
			Thread.dumpStack();
		}
		replaceAction(im, am, KeyEvent.VK_UP, upKap, oldUp);
		replaceAction(im, am, KeyEvent.VK_LEFT, leftKap, oldLeft);
		replaceAction(im, am, KeyEvent.VK_DOWN, downKap, oldDown);
		replaceAction(im, am, KeyEvent.VK_RIGHT, rightKap, oldRight);
		replaceAction(im, am, KeyEvent.VK_ENTER, enterKap, oldEnter);
		replaceAction(im, am, KeyEvent.VK_TAB, tabKap, oldTab);
		replaceAction(im, am, KeyEvent.VK_HOME, homeKap, oldHome);
		replaceAction(im, am, KeyEvent.VK_END, endKap, oldEnd);
		replaceAction(im, am, KeyEvent.VK_PAGE_UP, pageUpKap, oldPageUp);
		replaceAction(im, am, KeyEvent.VK_PAGE_DOWN, pageDownKap, oldPageDown);

		// Make Ctrl+C copy from description window.  This isn't done
		// automagically because the desc. window is not focusable, and copying
		// from text components can only be done from focused components.
		int key = KeyEvent.VK_C;
		KeyStroke ks = KeyStroke.getKeyStroke(key, InputEvent.CTRL_MASK);
		oldCtrlC.key = im.get(ks);
		im.put(ks, ctrlCKap.key);
		oldCtrlC.action = am.get(ctrlCKap.key);
		am.put(ctrlCKap.key, ctrlCKap.action);

		comp.addCaretListener(this);

	}


	public void mouseClicked(MouseEvent e) {
		if (e.getClickCount()==2) {
			insertSelectedCompletion();
		}
	}


	public void mouseEntered(MouseEvent e) {
	}


	public void mouseExited(MouseEvent e) {
	}


	public void mousePressed(MouseEvent e) {
	}


	public void mouseReleased(MouseEvent e) {
	}


	/**
	 * Positions the description window relative to the completion choices
	 * window.
	 */
	private void positionDescWindow() {

		boolean showDescWindow = descWindow!=null && ac.getShowDescWindow();
		if (!showDescWindow) {
			return;
		}

		Dimension screenSize = getToolkit().getScreenSize();
		//int totalH = Math.max(getHeight(), descWindow.getHeight());

		// Try to position to the right first.
		int x = getX() + getWidth() + 5;
		if (x+descWindow.getWidth()>screenSize.width) { // doesn't fit
			x = getX() - 5 - descWindow.getWidth();
		}

		int y = getY();
		if (aboveCaret) {
			y = y + getHeight() - descWindow.getHeight();
		}

		if (x!=descWindow.getX() || y!=descWindow.getY()) { 
			descWindow.setLocation(x, y);
		}

	}


	/**
	 * "Puts back" the original key/Action pair for a keystroke.  This is used
	 * when this popup is hidden.
	 *
	 * @param im The input map.
	 * @param am The action map.
	 * @param key The keystroke whose key/Action pair to change.
	 * @param kap The (original) key/Action pair.
	 * @see #replaceAction(InputMap, ActionMap, int, KeyActionPair, KeyActionPair)
	 */
	private void putBackAction(InputMap im, ActionMap am, int key,
								KeyActionPair kap) {
		KeyStroke ks = KeyStroke.getKeyStroke(key, 0);
		am.put(im.get(ks), kap.action); // Original action for the "new" key
		im.put(ks, kap.key); // Original key for the keystroke.
	}


	/**
	 * Replaces a key/Action pair in an InputMap and ActionMap with a new
	 * pair.
	 *
	 * @param im The input map.
	 * @param am The action map.
	 * @param key The keystroke whose information to replace.
	 * @param kap The new key/Action pair for <code>key</code>.
	 * @param old A buffer in which to place the old key/Action pair.
	 * @see #putBackAction(InputMap, ActionMap, int, KeyActionPair)
	 */
	private void replaceAction(InputMap im, ActionMap am, int key,
						KeyActionPair kap, KeyActionPair old) {
		KeyStroke ks = KeyStroke.getKeyStroke(key, 0);
		old.key = im.get(ks);
		im.put(ks, kap.key);
		old.action = am.get(kap.key);
		am.put(kap.key, kap.action);
	}


	/**
	 * Selects the first item in the completion list.
	 *
	 * @see #selectLastItem()
	 */
	private void selectFirstItem() {
		if (model.getSize() > 0) {
			list.setSelectedIndex(0);
			list.ensureIndexIsVisible(0);
		}
	}


	/**
	 * Selects the last item in the completion list.
	 *
	 * @see #selectFirstItem()
	 */
	private void selectLastItem() {
		int index = model.getSize() - 1;
		if (index > -1) {
			list.setSelectedIndex(index);
			list.ensureIndexIsVisible(index);
		}
	}


	/**
	 * Selects the next item in the completion list.
	 *
	 * @see #selectPreviousItem()
	 */
	private void selectNextItem() {
		int index = list.getSelectedIndex();
		if (index > -1) {
			index = (index + 1) % model.getSize();
			list.setSelectedIndex(index);
			list.ensureIndexIsVisible(index);
		}
	}


	/**
	 * Selects the completion item one "page down" from the currently selected
	 * one.
	 *
	 * @see #selectPageUpItem()
	 */
	private void selectPageDownItem() {
		int visibleRowCount = list.getVisibleRowCount();
		int i = Math.min(list.getModel().getSize()-1,
						list.getSelectedIndex()+visibleRowCount);
		list.setSelectedIndex(i);
		list.ensureIndexIsVisible(i);
	}


	/**
	 * Selects the completion item one "page up" from the currently selected
	 * one.
	 *
	 * @see #selectPageDownItem()
	 */
	private void selectPageUpItem() {
		int visibleRowCount = list.getVisibleRowCount();
		int i = Math.max(0, list.getSelectedIndex()-visibleRowCount);
		list.setSelectedIndex(i);
		list.ensureIndexIsVisible(i);
	}


	/**
	 * Selects the previous item in the completion list.
	 *
	 * @see #selectNextItem()
	 */
	private void selectPreviousItem() {
		int index = list.getSelectedIndex();
		switch (index) {
		case 0:
			index = list.getModel().getSize() - 1;
			break;
		case -1: // Check for an empty list (would be an error)
			index = list.getModel().getSize() - 1;
			if (index == -1) {
				return;
			}
			break;
		default:
			index = index - 1;
			break;
		}
		list.setSelectedIndex(index);
		list.ensureIndexIsVisible(index);
	}


	/**
	 * Sets the completions to display in the choices list.  The first
	 * completion is selected.
	 *
	 * @param completions The completions to display.
	 */
	public void setCompletions(List completions) {
		model.setContents(completions);
		selectFirstItem();
	}


	/**
	 * Sets the size of the description window.
	 *
	 * @param size The new size.  This cannot be <code>null</code>.
	 */
	public void setDescriptionWindowSize(Dimension size) {
		if (descWindow!=null) {
			descWindow.setSize(size);
		}
		else {
			preferredDescWindowSize = size;
		}
	}


	/**
	 * Sets the default list cell renderer to use when a completion provider
	 * does not supply its own.
	 *
	 * @param renderer The renderer to use.  If this is <code>null</code>,
	 *        a default renderer is used.
	 * @see #getListCellRenderer()
	 */
	public void setListCellRenderer(ListCellRenderer renderer) {
		DelegatingCellRenderer dcr = (DelegatingCellRenderer)list.
													getCellRenderer();
		dcr.setFallbackCellRenderer(renderer);
	}


	/**
	 * Sets the location of this window to be "good" relative to the specified
	 * rectangle.  That rectangle should be the location of the text
	 * component's caret, in screen coordinates.
	 *
	 * @param r The text component's caret position, in screen coordinates.
	 */
	public void setLocationRelativeTo(Rectangle r) {

		boolean showDescWindow = descWindow!=null && ac.getShowDescWindow();
		Dimension screenSize = getToolkit().getScreenSize();
		int totalH = getHeight();
		if (showDescWindow) {
			totalH = Math.max(totalH, descWindow.getHeight());
		}

		// Try putting our stuff "below" the caret first.  We assume that the
		// entire height of our stuff fits on the screen one way or the other.
		aboveCaret = false;
		int y = r.y + r.height + 10;
		if (y+totalH>screenSize.height) {
			y = r.y - 10 - getHeight();
			aboveCaret = true;
		}

		// Get x-coordinate of completions.  Try to align left edge with the
		// caret first.
		int x = r.x;
		if (x<0) {
			x = 0;
		}
		else if (x+getWidth()>screenSize.width) { // completions don't fit
			x = screenSize.width - getWidth();
		}

		setLocation(x, y);

		// Position the description window, if necessary.
		if (showDescWindow) {
			positionDescWindow();
		}

	}


	/**
	 * Toggles the visibility of this popup window.
	 *
	 * @param visible Whether this window should be visible.
	 */
	public void setVisible(boolean visible) {
		if (visible!=isVisible()) {
			if (visible) {
				installKeyBindings();
				lastLine = ac.getLineOfCaret();
				selectFirstItem();
				if (descWindow==null && ac.getShowDescWindow()) {
					descWindow = createDescriptionWindow();
					positionDescWindow();
					// descWindow needs a kick-start the first time it's
					// displayed.
					Completion c = (Completion)list.getSelectedValue();
					descWindow.setDescriptionFor(c);
				}
			}
			else {
				uninstallKeyBindings();
			}
			super.setVisible(visible);
			// Must set descWindow's visibility one way or the other each time,
			// because of the way child JWindows' visibility is handled - in
			// some ways it's dependent on the parent, in other ways it's not.
			if (descWindow!=null) {
				descWindow.setVisible(visible && ac.getShowDescWindow());
			}
		}

	}


	/**
	 * Stops intercepting certain keystrokes from the text component.
	 *
	 * @see #installKeyBindings()
	 */
	public void uninstallKeyBindings() {

		if (AutoCompletion.getDebug()) {
			System.out.println("PopupWindow: Removing keybindings");
		}

		JTextComponent comp = ac.getTextComponent();
		InputMap im = comp.getInputMap();
		ActionMap am = comp.getActionMap();

		putBackAction(im, am, KeyEvent.VK_ESCAPE, oldEscape);
		putBackAction(im, am, KeyEvent.VK_ESCAPE, oldEscape);
		putBackAction(im, am, KeyEvent.VK_UP, oldUp);
		putBackAction(im, am, KeyEvent.VK_DOWN, oldDown);
		putBackAction(im, am, KeyEvent.VK_LEFT, oldLeft);
		putBackAction(im, am, KeyEvent.VK_RIGHT, oldRight);
		putBackAction(im, am, KeyEvent.VK_ENTER, oldEnter);
		putBackAction(im, am, KeyEvent.VK_TAB, oldTab);
		putBackAction(im, am, KeyEvent.VK_HOME, oldHome);
		putBackAction(im, am, KeyEvent.VK_END, oldEnd);
		putBackAction(im, am, KeyEvent.VK_PAGE_UP, oldPageUp);
		putBackAction(im, am, KeyEvent.VK_PAGE_DOWN, oldPageDown);

		// Ctrl+C
		int key = KeyEvent.VK_C;
		KeyStroke ks = KeyStroke.getKeyStroke(key, InputEvent.CTRL_MASK);
		am.put(im.get(ks), oldCtrlC.action); // Original action
		im.put(ks, oldCtrlC.key); // Original key

		comp.removeCaretListener(this);

	}

	/**
	 * Updates the <tt>LookAndFeel</tt> of this window and the description
	 * window.
	 */
	public void updateUI() {
		SwingUtilities.updateComponentTreeUI(this);
		if (descWindow!=null) {
			descWindow.updateUI();
		}
	}


	/**
	 * Called when a new item is selected in the popup list.
	 *
	 * @param e The event.
	 */
	public void valueChanged(ListSelectionEvent e) {
		if (!e.getValueIsAdjusting()) {
			Object value = list.getSelectedValue();
			if (value!=null && descWindow!=null) {
				descWindow.setDescriptionFor((Completion)value);
				positionDescWindow();
			}
		}
	}


	class CopyAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			boolean doNormalCopy = false;
			if (descWindow!=null && descWindow.isVisible()) {
				doNormalCopy = !descWindow.copy();
			}
			if (doNormalCopy) {
				ac.getTextComponent().copy();
			}
		}

	}


	class DownAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (isVisible()) {
				selectNextItem();
			}
		}

	}


	class EndAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (isVisible()) {
				selectLastItem();
			}
		}

	}


	class EnterAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (isVisible()) {
				insertSelectedCompletion();
			}
		}

	}


	class EscapeAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (isVisible()) {
				setVisible(false);
			}
		}

	}


	class HomeAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (isVisible()) {
				selectFirstItem();
			}
		}

	}


	/**
	 * A mapping from a key (an Object) to an Action.
	 *
	 * @author Robert Futrell
	 * @version 1.0
	 */
	private static class KeyActionPair {

		public Object key;
		public Action action;

		public KeyActionPair() {
		}

		public KeyActionPair(Object key, Action a) {
			this.key = key;
			this.action = a;
		}

	}


	class LeftAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (isVisible()) {
				JTextComponent comp = ac.getTextComponent();
				Caret c = comp.getCaret();
				int dot = c.getDot();
				if (dot > 0) {
					c.setDot(--dot);
					// Ensure moving left hasn't moved us up a line, thus
					// hiding the popup window.
					if (comp.isVisible()) {
						if (lastLine!=-1) {
							doAutocomplete();
						}
					}
				}
			}
		}

	}


	class PageDownAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (isVisible()) {
				selectPageDownItem();
			}
		}

	}


	class PageUpAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (isVisible()) {
				selectPageUpItem();
			}
		}

	}


	class RightAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (isVisible()) {
				JTextComponent comp = ac.getTextComponent();
				Caret c = comp.getCaret();
				int dot = c.getDot();
				if (dot < comp.getDocument().getLength()) {
					c.setDot(++dot);
					// Ensure moving right hasn't moved us up a line, thus
					// hiding the popup window.
					if (comp.isVisible()) {
						if (lastLine!=-1) {
							doAutocomplete();
						}
					}
				}
			}
		}

	}


	class UpAction extends AbstractAction {

		public void actionPerformed(ActionEvent e) {
			if (isVisible()) {
				selectPreviousItem();
			}
		}

	}


}