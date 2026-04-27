<script lang="ts">
  import { onMount, onDestroy } from "svelte";
  import * as echarts from "echarts/core";
  import { LineChart, BarChart } from "echarts/charts";
  import {
    GridComponent,
    TooltipComponent,
    MarkLineComponent
  } from "echarts/components";
  import { CanvasRenderer } from "echarts/renderers";

  echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, MarkLineComponent, CanvasRenderer]);

  interface Bucket {
    label: string;
    kcal: number;
    iso: string;
  }

  let { buckets, target = 2000 }: { buckets: Bucket[]; target?: number } = $props();

  let el: HTMLDivElement;
  let chart: echarts.ECharts | undefined;

  function render() {
    if (!chart) return;
    chart.setOption({
      grid: { top: 16, right: 16, bottom: 28, left: 48 },
      tooltip: {
        trigger: "axis",
        backgroundColor: "#1C1C1F",
        borderColor: "#26262A",
        textStyle: { color: "#F5F4EF" },
        formatter: (params: any[]) => {
          const p = params[0];
          return `<div style="font-size:11px;color:#B5B3AC;margin-bottom:4px">${p.name}</div>` +
            `<div style="font-weight:600">${Math.round(p.value)} kcal</div>`;
        }
      },
      xAxis: {
        type: "category",
        data: buckets.map((b) => b.label),
        axisLine: { lineStyle: { color: "#26262A" } },
        axisLabel: { color: "#6E6C66", fontSize: 11 }
      },
      yAxis: {
        type: "value",
        splitLine: { lineStyle: { color: "#1C1C1F" } },
        axisLabel: { color: "#6E6C66", fontSize: 11 }
      },
      series: [
        {
          type: "bar",
          data: buckets.map((b) => b.kcal),
          itemStyle: {
            color: "#C7F25C",
            borderRadius: [4, 4, 0, 0]
          },
          markLine: target > 0 ? {
            symbol: "none",
            silent: true,
            lineStyle: { color: "#6E6C66", type: "dashed" },
            label: { color: "#6E6C66", fontSize: 10, formatter: `${target} kcal target` },
            data: [{ yAxis: target }]
          } : undefined
        }
      ]
    });
  }

  onMount(() => {
    chart = echarts.init(el);
    render();
    const ro = new ResizeObserver(() => chart?.resize());
    ro.observe(el);
    return () => ro.disconnect();
  });

  onDestroy(() => {
    chart?.dispose();
  });

  $effect(() => {
    void buckets;
    void target;
    render();
  });
</script>

<div bind:this={el} class="w-full h-48"></div>
