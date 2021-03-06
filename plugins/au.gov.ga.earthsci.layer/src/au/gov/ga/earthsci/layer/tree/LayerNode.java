/*******************************************************************************
 * Copyright 2012 Geoscience Australia
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package au.gov.ga.earthsci.layer.tree;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.event.Message;
import gov.nasa.worldwind.globes.ElevationModel;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.render.DrawContext;

import java.awt.Point;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Set;

import au.gov.ga.earthsci.common.persistence.Adapter;
import au.gov.ga.earthsci.common.persistence.Persistent;
import au.gov.ga.earthsci.common.util.IInformationed;
import au.gov.ga.earthsci.common.util.ILoader;
import au.gov.ga.earthsci.common.util.Util;
import au.gov.ga.earthsci.core.model.IModelStatus;
import au.gov.ga.earthsci.core.model.IStatused;
import au.gov.ga.earthsci.layer.DrawOrder;
import au.gov.ga.earthsci.layer.IPersistentLayer;
import au.gov.ga.earthsci.layer.LayerPersistentAdapter;
import au.gov.ga.earthsci.layer.Messages;
import au.gov.ga.earthsci.layer.delegator.AbstractLayerDelegator;
import au.gov.ga.earthsci.layer.delegator.ILayerDelegator;
import au.gov.ga.earthsci.layer.delegator.PersistentLayerDelegator;
import au.gov.ga.earthsci.layer.elevation.IElevationModelLayer;
import au.gov.ga.earthsci.worldwind.common.layers.Bounded;
import au.gov.ga.earthsci.worldwind.common.layers.Bounds;
import au.gov.ga.earthsci.worldwind.common.util.AVKeyMore;

/**
 * Layer tree node implementation for layers. Implements the {@link Layer}
 * interface, and delegates all layer methods to a
 * {@link AbstractLayerDelegator} object.
 * <p/>
 * Also fires a property change in all setter methods to comply with the Java
 * Bean specification.
 * 
 * @author Michael de Hoog (michael.dehoog@ga.gov.au)
 */
public class LayerNode extends AbstractLayerTreeNode implements ILayerNode
{
	protected PersistentLayerDelegator delegator = new PersistentLayerDelegator();
	private boolean loading = false;
	private Integer drawOrder = null;
	private Integer drawOrderCached = null;

	public LayerNode()
	{
		//propagate property changes from the delegate to the listeners of the node
		delegator.addPropertyChangeListener(new PropertyChangeListener()
		{
			@Override
			public void propertyChange(PropertyChangeEvent evt)
			{
				firePropertyChange(new PropertyChangeEvent(LayerNode.this, evt.getPropertyName(), evt.getOldValue(),
						evt.getNewValue()));

				//new layer, update required information:
				if (evt.getPropertyName().equals("layer")) //$NON-NLS-1$
				{
					//clear the cached draw order, as new layer may have a different default
					drawOrderCached = null;

					//get the legend url if it exists (set by the LayerFactory)
					URL legendURL = (URL) getValue(AVKeyMore.LEGEND_URL);
					if (legendURL == null)
					{
						AVList constructionParameters = (AVList) getValue(AVKey.CONSTRUCTION_PARAMETERS);
						if (constructionParameters != null)
						{
							legendURL = (URL) constructionParameters.getValue(AVKeyMore.LEGEND_URL);
						}
					}
					if (legendURL != null)
					{
						setLegendURL(legendURL);
					}

					//update layer list (draw order may have changed), and elevation models if required
					updateLayers();
					if (getElevationModel() != null)
					{
						updateElevationModels();
					}
				}
			}
		});
		delegator.setPropertiesChangedTrackingEnabled(false);
		delegator.setName(Messages.LayerNode_Loading);
		delegator.setPropertiesChangedTrackingEnabled(true);
	}

	@Override
	public Class<IPersistentLayer> getLayerClass()
	{
		return delegator.getLayerClass();
	}

	@Override
	public boolean isLayerSet()
	{
		return delegator.isLayerSet();
	}

	@Override
	public boolean isGrandLayerSet()
	{
		return delegator.isGrandLayerSet();
	}

	@Override
	public IPersistentLayer getLayer()
	{
		return delegator.getLayer();
	}

	@Override
	public void setLayer(IPersistentLayer layer)
	{
		delegator.setLayer(layer);
		drawOrderCached = null;
	}

	@Persistent(name = "definition")
	@Adapter(LayerPersistentAdapter.class)
	private IPersistentLayer getPersistentLayer()
	{
		if (isLayerSet())
		{
			return getLayer();
		}
		return null;
	}

	//for unpersistence
	@SuppressWarnings("unused")
	private void setPersistentLayer(IPersistentLayer layer)
	{
		setLayer(layer);
	}

	@Override
	public Layer getGrandLayer()
	{
		return delegator.getGrandLayer();
	}

	protected <T> T getDelegateImplementing(Class<T> c)
	{
		Layer layer = this.delegator;
		while (true)
		{
			if (c.isInstance(layer))
			{
				return c.cast(layer);
			}
			if (!(layer instanceof ILayerDelegator))
			{
				return null;
			}
			layer = ((ILayerDelegator<?>) layer).getLayer();
		}
	}

	@Override
	public ElevationModel getElevationModel()
	{
		IElevationModelLayer elevationModelLayer = getDelegateImplementing(IElevationModelLayer.class);
		if (elevationModelLayer != null)
		{
			return elevationModelLayer.getElevationModel();
		}
		return null;
	}

	@Override
	public URL getInformationURL()
	{
		//if the layer is IInformationed, use that instead
		IInformationed informationed = getDelegateImplementing(IInformationed.class);
		if (informationed != null)
		{
			URL url = informationed.getInformationURL();
			if (url != null)
			{
				return url;
			}
		}
		return super.getInformationURL();
	}

	@Override
	public String getInformationString()
	{
		//if the layer is IInformationed, use that instead
		IInformationed informationed = getDelegateImplementing(IInformationed.class);
		if (informationed != null)
		{
			String information = informationed.getInformationString();
			if (!Util.isEmpty(information))
			{
				return information;
			}
		}
		return super.getInformationString();
	}

	@Override
	public IModelStatus getStatus()
	{
		IStatused statused = getDelegateImplementing(IStatused.class);
		if (statused != null)
		{
			return statused.getStatus();
		}
		return super.getStatus();
	}

	@Override
	public Bounds getBounds()
	{
		Bounded bounded = getDelegateImplementing(Bounded.class);
		if (bounded != null)
		{
			return bounded.getBounds();
		}
		return null;
	}

	@Override
	public boolean isFollowTerrain()
	{
		Bounded bounded = getDelegateImplementing(Bounded.class);
		if (bounded != null)
		{
			return bounded.isFollowTerrain();
		}
		return false;
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt)
	{
		firePropertyChange(evt);
	}

	@Override
	public boolean isLoading()
	{
		if (loading)
		{
			return true;
		}
		ILoader loader = getDelegateImplementing(ILoader.class);
		if (loader != null)
		{
			return loader.isLoading();
		}
		return false;
	}

	@Override
	public void setLoading(boolean loading)
	{
		firePropertyChange("loading", isLoading(), this.loading = loading); //$NON-NLS-1$
	}

	@Override
	public int getDrawOrder()
	{
		if (drawOrder != null)
		{
			return drawOrder;
		}
		if (drawOrderCached != null)
		{
			return drawOrderCached;
		}
		Class<? extends Layer> layerClass = Layer.class;
		Layer l = getGrandLayer();
		if (l != null)
		{
			layerClass = l.getClass();
		}
		drawOrderCached = DrawOrder.getDefaultDrawOrder(layerClass);
		return drawOrderCached;
	}

	@Override
	public void setDrawOrder(int drawOrder)
	{
		firePropertyChange("drawOrder", getDrawOrder(), this.drawOrder = drawOrder); //$NON-NLS-1$
		updateLayers(); //need to update the layer list, as it is sorted by draw order
	}

	@Persistent(name = "drawOrder", attribute = true)
	private Integer getPersistentDrawOrder()
	{
		return drawOrder;
	}

	@SuppressWarnings("unused")
	private void setPersistentDrawOrder(Integer drawOrder)
	{
		setDrawOrder(drawOrder);
	}

	//////////////////////
	// Layer delegation //
	//////////////////////

	@Override
	public void dispose()
	{
		delegator.dispose();
	}

	@Override
	public void onMessage(Message msg)
	{
		delegator.onMessage(msg);
	}

	@Override
	public Object setValue(String key, Object value)
	{
		return delegator.setValue(key, value);
	}

	@Persistent(attribute = true)
	@Override
	public boolean isEnabled()
	{
		return delegator.isEnabled();
	}

	@Override
	public void setEnabled(boolean enabled)
	{
		delegator.setEnabled(enabled);
		if (enabled && getOpacity() <= 0.0)
		{
			setOpacity(1.0);
		}
	}

	@Override
	public String getName()
	{
		return delegator.getName();
	}

	@Override
	public void setName(String name)
	{
		delegator.setName(name);
	}

	@Override
	public AVList setValues(AVList avList)
	{
		return delegator.setValues(avList);
	}

	@Override
	public String getRestorableState()
	{
		return delegator.getRestorableState();
	}

	@Persistent(attribute = true)
	@Override
	public double getOpacity()
	{
		return delegator.getOpacity();
	}

	@Override
	public void restoreState(String stateInXml)
	{
		delegator.restoreState(stateInXml);
	}

	@Override
	public Object getValue(String key)
	{
		return delegator.getValue(key);
	}

	@Override
	public void setOpacity(double opacity)
	{
		delegator.setOpacity(opacity);
	}

	@Override
	public Collection<Object> getValues()
	{
		return delegator.getValues();
	}

	@Override
	public String getStringValue(String key)
	{
		return delegator.getStringValue(key);
	}

	@Override
	public boolean isPickEnabled()
	{
		return delegator.isPickEnabled();
	}

	@Override
	public Set<Entry<String, Object>> getEntries()
	{
		return delegator.getEntries();
	}

	@Override
	public boolean hasKey(String key)
	{
		return delegator.hasKey(key);
	}

	@Override
	public Object removeKey(String key)
	{
		return delegator.removeKey(key);
	}

	@Override
	public void setPickEnabled(boolean isPickable)
	{
		delegator.setPickEnabled(isPickable);
	}

	@Override
	public void preRender(DrawContext dc)
	{
		delegator.preRender(dc);
	}

	@Override
	public void render(DrawContext dc)
	{
		delegator.render(dc);
	}

	@Override
	public void pick(DrawContext dc, Point pickPoint)
	{
		delegator.pick(dc, pickPoint);
	}

	@Override
	public boolean isAtMaxResolution()
	{
		return delegator.isAtMaxResolution();
	}

	@Override
	public boolean isMultiResolution()
	{
		return delegator.isMultiResolution();
	}

	@Override
	public double getScale()
	{
		return delegator.getScale();
	}

	@Override
	public boolean isNetworkRetrievalEnabled()
	{
		return delegator.isNetworkRetrievalEnabled();
	}

	@Override
	public void setNetworkRetrievalEnabled(boolean networkRetrievalEnabled)
	{
		delegator.setNetworkRetrievalEnabled(networkRetrievalEnabled);
	}

	@Override
	public AVList copy()
	{
		return delegator.copy();
	}

	@Override
	public void setExpiryTime(long expiryTime)
	{
		delegator.setExpiryTime(expiryTime);
	}

	@Override
	public AVList clearList()
	{
		return delegator.clearList();
	}

	@Override
	public long getExpiryTime()
	{
		return delegator.getExpiryTime();
	}

	@Override
	public double getMinActiveAltitude()
	{
		return delegator.getMinActiveAltitude();
	}

	@Override
	public void setMinActiveAltitude(double minActiveAltitude)
	{
		delegator.setMinActiveAltitude(minActiveAltitude);
	}

	@Override
	public double getMaxActiveAltitude()
	{
		return delegator.getMaxActiveAltitude();
	}

	@Override
	public void setMaxActiveAltitude(double maxActiveAltitude)
	{
		delegator.setMaxActiveAltitude(maxActiveAltitude);
	}

	@Override
	public boolean isLayerInView(DrawContext dc)
	{
		return delegator.isLayerInView(dc);
	}

	@Override
	public boolean isLayerActive(DrawContext dc)
	{
		return delegator.isLayerActive(dc);
	}

	@Override
	public Double getMaxEffectiveAltitude(Double radius)
	{
		return delegator.getMaxEffectiveAltitude(radius);
	}

	@Override
	public Double getMinEffectiveAltitude(Double radius)
	{
		return delegator.getMinEffectiveAltitude(radius);
	}
}
