import * as d3 from "d3";
import { Div } from "./Div";
import { Svg } from "./Svg";

export class PieChart extends Div {

    constructor(private width: number, private className: string, private data: any[]) {
        super(null, { className: "marginBottom" });
    }

    override preRender(): boolean {
        this.setChildren([new Svg(null, { className: this.className })]);
        return true;
    }

    override domPreUpdateEvent = () => {
        const svg = d3.select("." + this.className);

        // width/height must match d3PieChart scss class
        const height = this.width;
        const radius = Math.min(this.width, height) / 2;
        const g = svg.append("g").attr("transform", "translate(" + this.width / 2 + "," + height / 2 + ")");

        const colors = [];
        for (const d of this.data) {
            colors.push(d.color);
        }

        const color = d3.scaleOrdinal(colors);

        // Generate the pie
        const pie = d3.pie().value((d: any) => { return d.value; });

        // Generate the arcs
        const arc: any = d3.arc()
            .innerRadius(0)
            .outerRadius(radius);

        // Generate groups
        const arcs = g.selectAll("." + this.className)
            .data(pie(this.data))
            .enter()
            .append("g")
            .attr("class", "arc");

        // Draw arc paths
        arcs.append("path")
            .attr("fill", (d: any, i: any) => {
                return color(i);
            })
            .attr("d", arc);

        arcs.append("text")
            .attr("transform", (d: any) => {
                d.innerRadius = 0;
                d.outerRadius = this.width;
                return "translate(" + arc.centroid(d) + ")";
            })
            .attr("text-anchor", "middle")
            .text((d: any, i: any) => {
                return this.data[i].label;
            });
    }
}
